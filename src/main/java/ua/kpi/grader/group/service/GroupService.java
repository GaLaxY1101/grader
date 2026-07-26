package ua.kpi.grader.group.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.kpi.grader.common.exception.ResourceNotFoundException;
import ua.kpi.grader.group.dto.CreateGroupRequest;
import ua.kpi.grader.group.dto.GroupResponse;
import ua.kpi.grader.group.dto.GroupStudentResponse;
import ua.kpi.grader.group.dto.UpdateGroupRequest;
import ua.kpi.grader.group.entity.AcademicGroup;
import ua.kpi.grader.group.entity.GroupStudent;
import ua.kpi.grader.group.repository.AcademicGroupRepository;
import ua.kpi.grader.group.repository.GroupStudentRepository;
import ua.kpi.grader.user.entity.Student;
import ua.kpi.grader.user.repository.StudentRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GroupService {

    private final AcademicGroupRepository groupRepository;
    private final GroupStudentRepository groupStudentRepository;
    private final StudentRepository studentRepository;

    /**
     * Returns all active groups (is_active = true).
     *
     * @return list of active GroupResponse DTOs
     */
    @Transactional(readOnly = true)
    public List<GroupResponse> findAllActive() {
        return groupRepository.findAllByIsActiveTrue().stream()
                .map(GroupResponse::from)
                .toList();
    }

    /**
     * Returns a group by its primary key.
     *
     * @param id the group ID
     * @return the matching GroupResponse DTO
     * @throws ResourceNotFoundException if no group exists with that ID
     */
    @Transactional(readOnly = true)
    public GroupResponse findById(Long id) {
        return GroupResponse.from(findGroupOrThrow(id));
    }

    /**
     * Creates a new academic group.
     *
     * @param request the creation payload
     * @return the persisted GroupResponse DTO
     * @throws IllegalStateException if the group code is already taken
     */
    @Transactional
    public GroupResponse createGroup(CreateGroupRequest request) {
        if (groupRepository.existsByCode(request.code())) {
            throw new IllegalStateException("Group with code '" + request.code() + "' already exists");
        }
        AcademicGroup group = AcademicGroup.builder()
                .code(request.code())
                .faculty(request.faculty())
                .speciality(request.speciality())
                .yearOfCreation(request.yearOfCreation())
                .build();
        return GroupResponse.from(groupRepository.save(group));
    }

    /**
     * Updates the mutable fields of an existing group.
     *
     * @param id      the group ID
     * @param request the update payload
     * @return the updated GroupResponse DTO
     * @throws ResourceNotFoundException if no group exists with that ID
     * @throws IllegalStateException     if the new code is already taken by another group
     */
    @Transactional
    public GroupResponse updateGroup(Long id, UpdateGroupRequest request) {
        AcademicGroup group = findGroupOrThrow(id);
        if (groupRepository.existsByCodeAndIdNot(request.code(), id)) {
            throw new IllegalStateException("Group with code '" + request.code() + "' already exists");
        }
        group.update(request.code(), request.faculty(), request.speciality(), request.yearOfCreation());
        return GroupResponse.from(group);
    }

    /**
     * Soft-deletes a group by setting is_active = false.
     *
     * @param id the group ID
     * @throws ResourceNotFoundException if no group exists with that ID
     */
    @Transactional
    public void deactivateGroup(Long id) {
        AcademicGroup group = findGroupOrThrow(id);
        group.deactivate();
    }

    /**
     * Adds a student to a group. A student may hold at most one active
     * (non-graduated) group membership at a time.
     *
     * @param groupId   the group ID
     * @param studentId the student profile ID
     * @return the created GroupStudentResponse DTO
     * @throws ResourceNotFoundException if the group or student does not exist
     * @throws IllegalStateException     if the student is already a member of the group,
     *                                   or already has an active membership in another group
     */
    @Transactional
    public GroupStudentResponse addStudent(Long groupId, Long studentId) {
        AcademicGroup group = findGroupOrThrow(groupId);
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Student not found with id: " + studentId));
        if (groupStudentRepository.existsByGroupIdAndStudentId(groupId, studentId)) {
            throw new IllegalStateException(
                    "Student " + studentId + " is already a member of group " + groupId);
        }
        groupStudentRepository.findActiveByStudentId(studentId).ifPresent(existing -> {
            throw new IllegalStateException(
                    "Student " + studentId + " already has an active membership in group "
                            + existing.getGroup().getId());
        });
        GroupStudent membership = GroupStudent.builder()
                .group(group)
                .student(student)
                .build();
        return GroupStudentResponse.from(groupStudentRepository.save(membership));
    }

    /**
     * Removes a student from a group.
     *
     * @param groupId   the group ID
     * @param studentId the student profile ID
     * @throws ResourceNotFoundException if the group does not exist or the student is not in the group
     */
    @Transactional
    public void removeStudent(Long groupId, Long studentId) {
        findGroupOrThrow(groupId);
        GroupStudent membership = groupStudentRepository
                .findByGroupIdAndStudentId(groupId, studentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Student " + studentId + " is not a member of group " + groupId));
        groupStudentRepository.delete(membership);
    }

    /**
     * Returns all students belonging to a group.
     *
     * @param groupId the group ID
     * @return list of GroupStudentResponse DTOs
     * @throws ResourceNotFoundException if no group exists with that ID
     */
    @Transactional(readOnly = true)
    public List<GroupStudentResponse> findStudents(Long groupId) {
        findGroupOrThrow(groupId);
        return groupStudentRepository.findAllByGroupIdWithStudentUser(groupId).stream()
                .map(GroupStudentResponse::from)
                .toList();
    }

    private AcademicGroup findGroupOrThrow(Long id) {
        return groupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Group not found with id: " + id));
    }
}
