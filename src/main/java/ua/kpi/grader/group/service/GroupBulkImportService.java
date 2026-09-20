package ua.kpi.grader.group.service;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ua.kpi.grader.common.exception.InvalidImportException;
import ua.kpi.grader.common.exception.InvalidImportException.RowError;
import ua.kpi.grader.common.exception.InvalidPhoneNumberException;
import ua.kpi.grader.common.exception.ResourceNotFoundException;
import ua.kpi.grader.common.util.PhoneNumberNormalizer;
import ua.kpi.grader.group.dto.BulkCommitRequest;
import ua.kpi.grader.group.dto.BulkCommitWithGroupRequest;
import ua.kpi.grader.group.dto.BulkImportResult;
import ua.kpi.grader.group.dto.BulkImportResult.ImportedStudent;
import ua.kpi.grader.group.dto.CreateGroupRequest;
import ua.kpi.grader.group.entity.AcademicGroup;
import ua.kpi.grader.group.entity.GroupStudent;
import ua.kpi.grader.group.repository.AcademicGroupRepository;
import ua.kpi.grader.group.repository.GroupStudentRepository;
import ua.kpi.grader.user.dto.CreateUserRequest;
import ua.kpi.grader.user.dto.ParsedStudentRow;
import ua.kpi.grader.user.dto.ParsedStudentsResponse;
import ua.kpi.grader.user.dto.StudentColumnMapping;
import ua.kpi.grader.user.dto.StudentInput;
import ua.kpi.grader.user.dto.UserResponse;
import ua.kpi.grader.user.entity.Role;
import ua.kpi.grader.user.entity.Student;
import ua.kpi.grader.user.entity.User;
import ua.kpi.grader.user.repository.StudentRepository;
import ua.kpi.grader.user.repository.UserRepository;
import ua.kpi.grader.user.service.StudentXlsxReader;
import ua.kpi.grader.user.service.UserService;


import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Orchestrates bulk import of students into an {@link AcademicGroup}.
 *
 * <p>Split into two independent phases:
 * <ul>
 *     <li><b>Parse</b> — stateless read of an uploaded XLSX. Never writes.</li>
 *     <li><b>Commit</b> — takes the finalised (teacher-reviewed) list of students
 *         and creates them + links them to the target group in a single
 *         transaction.</li>
 * </ul>
 *
 * <p>Commit semantics: pre-validation is exhaustive; if any row fails
 * bean-validation, duplicates another row's email, or belongs to a student
 * already active in a different group, the whole batch is rejected with
 * {@link InvalidImportException} and no writes occur. Once pre-validation
 * passes, {@link UserService#createUser} is called per new row. That call
 * provisions the user in Keycloak <em>before</em> the local DB insert, so a
 * failure between two Keycloak calls will leave orphan Keycloak users — same
 * caveat the single-user endpoint accepts. Pre-validation minimises the
 * failure surface.
 */
@Service
@RequiredArgsConstructor
public class GroupBulkImportService {

    private final AcademicGroupRepository groupRepository;
    private final GroupStudentRepository groupStudentRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final StudentXlsxReader xlsxReader;
    private final PhoneNumberNormalizer phoneNumberNormalizer;
    private final UserService userService;
    private final Validator validator;

    /**
     * Parses an uploaded XLSX into per-row DTOs without writing anything.
     * Row-level issues (missing required cell, invalid phone) are recorded
     * on the returned rows, not thrown, so the frontend can display every
     * problem at once for inline editing.
     *
     * @param file    the uploaded XLSX file
     * @param mapping teacher-supplied column mapping
     * @return parsed rows plus the headers actually detected in the file
     */
    public ParsedStudentsResponse parseFile(MultipartFile file, StudentColumnMapping mapping) {
        try {
            List<String> headers = xlsxReader.readHeaders(file.getInputStream());
            List<ParsedStudentRow> rows = xlsxReader.read(file.getInputStream(), mapping);
            return new ParsedStudentsResponse(rows, headers);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read uploaded file", e);
        }
    }

    /**
     * Creates a new {@link AcademicGroup} and commits the batch of students
     * into it in one transaction. If pre-validation of the students fails,
     * the group is not created either.
     *
     * @param request group creation payload plus the finalised student list
     * @return the import result classifying every row as created or linked
     * @throws IllegalStateException      if the group code is already taken
     * @throws InvalidImportException     if any row fails pre-validation
     */
    @Transactional
    public BulkImportResult commitWithNewGroup(BulkCommitWithGroupRequest request) {
        validateStudents(request.students(), null);
        AcademicGroup group = createGroup(request.group());
        return doCommit(group, request.students());
    }

    /**
     * Commits a batch of students into an existing {@link AcademicGroup}
     * in one transaction.
     *
     * @param groupId the target group id
     * @param request the finalised student list
     * @return the import result classifying every row as created or linked
     * @throws ResourceNotFoundException if the group does not exist
     * @throws InvalidImportException    if any row fails pre-validation
     */
    @Transactional
    public BulkImportResult commitIntoGroup(Long groupId, BulkCommitRequest request) {
        AcademicGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found with id: " + groupId));
        validateStudents(request.students(), group.getId());
        return doCommit(group, request.students());
    }

    // ─── internals ─────────────────────────────────────────────────────────

    private AcademicGroup createGroup(CreateGroupRequest req) {
        if (groupRepository.existsByCode(req.code())) {
            throw new IllegalStateException("Group with code '" + req.code() + "' already exists");
        }
        return groupRepository.save(AcademicGroup.builder()
                .code(req.code())
                .faculty(req.faculty())
                .speciality(req.speciality())
                .yearOfCreation(req.yearOfCreation())
                .build());
    }

    /**
     * Runs pre-commit validation across the whole batch. Throws
     * {@link InvalidImportException} carrying every problem found — the client
     * highlights all offending rows in one pass.
     *
     * @param students      the batch to validate
     * @param targetGroupId the group the batch will be committed into, or
     *                      {@code null} when the group does not yet exist
     *                      (create-group-and-commit flow). Used to distinguish
     *                      an idempotent re-import (student already in the
     *                      target group) from a real conflict (student active
     *                      in a different group).
     */
    private void validateStudents(List<StudentInput> students, Long targetGroupId) {
        List<RowError> errors = new ArrayList<>();
        Map<String, Integer> firstSeenByEmail = new HashMap<>();

        for (int i = 0; i < students.size(); i++) {
            int rowNumber = i + 1;
            StudentInput row = students.get(i);

            for (ConstraintViolation<StudentInput> v : validator.validate(row)) {
                errors.add(new RowError(rowNumber, v.getPropertyPath().toString(), v.getMessage()));
            }

            if (row.phone() != null && !row.phone().isBlank()) {
                try {
                    phoneNumberNormalizer.normalizeUa(row.phone());
                } catch (InvalidPhoneNumberException ex) {
                    errors.add(new RowError(rowNumber, "phone", ex.getMessage()));
                }
            }

            if (row.email() != null) {
                String key = row.email().toLowerCase();
                Integer firstRow = firstSeenByEmail.putIfAbsent(key, rowNumber);
                if (firstRow != null) {
                    errors.add(new RowError(rowNumber, "email",
                            "duplicate of row " + firstRow + " within this batch"));
                }
            }
        }

        for (int i = 0; i < students.size(); i++) {
            int rowNumber = i + 1;
            String email = students.get(i).email();
            if (email == null) continue;
            Optional<User> existingUser = userRepository.findByEmail(email);
            if (existingUser.isEmpty()) continue;
            Optional<Student> existingStudent = studentRepository.findByUserId(existingUser.get().getId());
            if (existingStudent.isEmpty()) continue;
            Optional<GroupStudent> activeMembership = groupStudentRepository
                    .findActiveByStudentId(existingStudent.get().getId());
            if (activeMembership.isEmpty()) continue;
            Long activeGroupId = activeMembership.get().getGroup().getId();
            if (targetGroupId != null && targetGroupId.equals(activeGroupId)) continue;
            errors.add(new RowError(rowNumber, "email",
                    "student is already active in group '"
                            + activeMembership.get().getGroup().getCode() + "'"));
        }

        if (!errors.isEmpty()) {
            throw new InvalidImportException(errors);
        }
    }

    /**
     * Executes the write phase. Assumes {@link #validateStudents} has already run,
     * so business-rule violations here are unexpected and will roll back the
     * transaction.
     */
    private BulkImportResult doCommit(AcademicGroup group, List<StudentInput> students) {
        List<ImportedStudent> created = new ArrayList<>();
        List<ImportedStudent> linked = new ArrayList<>();
        Set<Long> studentsAlreadyLinkedToThisGroup = new HashSet<>();

        for (StudentInput input : students) {
            String normalisedPhone = phoneNumberNormalizer.normalizeUa(input.phone());
            Optional<User> existingUser = userRepository.findByEmail(input.email());
            User user;
            boolean userIsNew;
            if (existingUser.isPresent()) {
                user = existingUser.get();
                userIsNew = false;
            } else {
                UserResponse createdUser = userService.createUser(new CreateUserRequest(
                        input.email(),
                        input.firstName(),
                        input.lastName(),
                        normalisedPhone,
                        null,
                        Role.STUDENT));
                user = userRepository.findById(createdUser.id())
                        .orElseThrow(() -> new IllegalStateException(
                                "User just created but not found: " + input.email()));
                userIsNew = true;
            }

            Student student = studentRepository.findByUserId(user.getId())
                    .orElseGet(() -> studentRepository.save(Student.builder().user(user).build()));

            if (!groupStudentRepository.existsByGroupIdAndStudentId(group.getId(), student.getId())
                    && studentsAlreadyLinkedToThisGroup.add(student.getId())) {
                groupStudentRepository.save(GroupStudent.builder()
                        .group(group)
                        .student(student)
                        .build());
            }

            ImportedStudent imported = new ImportedStudent(user.getId(), student.getId(), user.getEmail());
            (userIsNew ? created : linked).add(imported);
        }

        return new BulkImportResult(group.getId(), students.size(), created, linked);
    }
}
