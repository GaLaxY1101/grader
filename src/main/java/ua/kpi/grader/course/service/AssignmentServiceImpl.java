package ua.kpi.grader.course.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.kpi.grader.common.exception.ResourceNotFoundException;
import ua.kpi.grader.course.dto.AssignmentResponse;
import ua.kpi.grader.course.dto.CreateAssignmentRequest;
import ua.kpi.grader.course.dto.ProgrammingTaskDetails;
import ua.kpi.grader.course.dto.UpdateAssignmentRequest;
import ua.kpi.grader.course.entity.Assignment;
import ua.kpi.grader.course.entity.Course;
import ua.kpi.grader.course.entity.Language;
import ua.kpi.grader.course.entity.ProgrammingTask;
import ua.kpi.grader.course.entity.TestMode;
import ua.kpi.grader.course.repository.AssignmentRepository;
import ua.kpi.grader.course.repository.CourseRepository;
import ua.kpi.grader.user.entity.Teacher;
import ua.kpi.grader.security.CurrentUser;
import ua.kpi.grader.user.repository.TeacherRepository;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AssignmentServiceImpl implements AssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final CourseRepository courseRepository;
    private final TeacherRepository teacherRepository;
    private final CurrentUser currentUser;

    /**
     * Returns all active assignments for a course.
     *
     * @param courseId the course ID
     * @return list of AssignmentResponse DTOs
     * @throws ResourceNotFoundException if the course does not exist
     */
    @Override
    @Transactional(readOnly = true)
    public List<AssignmentResponse> findAllByCourse(Long courseId) {
        findCourseOrThrow(courseId);
        return assignmentRepository.findAllByCourseIdAndIsActiveTrue(courseId).stream()
                .map(AssignmentResponse::from)
                .toList();
    }

    /**
     * Returns an active assignment by its ID.
     *
     * @param id the assignment ID
     * @return AssignmentResponse DTO
     * @throws ResourceNotFoundException if no active assignment exists with that ID
     */
    @Override
    @Transactional(readOnly = true)
    public AssignmentResponse findById(Long id) {
        Assignment assignment = assignmentRepository.findByIdAndIsActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Assignment not found with id: " + id));
        return AssignmentResponse.from(assignment);
    }

    /**
     * Creates a new assignment and optional programming task for a course in a single transaction.
     * The teacher is resolved from the Keycloak JWT email claim.
     *
     * @param courseId the course ID
     * @param request  the creation payload
     * @return the persisted AssignmentResponse DTO
     * @throws ResourceNotFoundException if the course or teacher does not exist
     */
    @Override
    @Transactional
    public AssignmentResponse createAssignment(Long courseId, CreateAssignmentRequest request) {
        Course course = findCourseOrThrow(courseId);

        // TODO: resolve Keycloak UUID to internal teacher id
        // This will be done when we implement user sync in auth module
        String email = currentUser.getEmail();
        Teacher teacher = teacherRepository.findByUser_Email(email)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Teacher not found for user: " + email));

        Assignment assignment = Assignment.builder()
                .course(course)
                .title(request.title())
                .description(request.description())
                .maxScore(request.maxScore() != null ? request.maxScore() : 100)
                .deadline(toOffsetDateTime(request.deadline()))
                .createdBy(teacher)
                .build();

        if (request.programmingTask() != null) {
            assignment.setProgrammingTask(buildProgrammingTask(assignment, request.programmingTask()));
        }

        return AssignmentResponse.from(assignmentRepository.save(assignment));
    }

    /**
     * Updates an existing assignment. The code check (programming task) is toggled by the
     * presence of {@code request.programmingTask()}: non-null creates or updates it, null
     * removes any existing one via orphan removal.
     *
     * @param id      the assignment ID
     * @param request the update payload
     * @return the updated AssignmentResponse DTO
     * @throws ResourceNotFoundException if no active assignment exists with that ID
     */
    @Override
    @Transactional
    public AssignmentResponse updateAssignment(Long id, UpdateAssignmentRequest request) {
        Assignment assignment = assignmentRepository.findByIdAndIsActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Assignment not found with id: " + id));
        assignment.update(
                request.title(),
                request.description(),
                request.maxScore(),
                toOffsetDateTime(request.deadline())
        );

        ProgrammingTaskDetails incoming = request.programmingTask();
        ProgrammingTask existing = assignment.getProgrammingTask();

        if (incoming != null && existing == null) {
            assignment.setProgrammingTask(buildProgrammingTask(assignment, incoming));
        } else if (incoming != null) {
            updateProgrammingTask(existing, incoming);
        } else if (existing != null) {
            assignment.setProgrammingTask(null);
        }

        return AssignmentResponse.from(assignment);
    }

    /**
     * Soft-deletes an assignment by setting is_active = false.
     *
     * @param id the assignment ID
     * @throws ResourceNotFoundException if no active assignment exists with that ID
     */
    @Override
    @Transactional
    public void deactivateAssignment(Long id) {
        Assignment assignment = assignmentRepository.findByIdAndIsActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Assignment not found with id: " + id));
        assignment.deactivate();
    }

    private ProgrammingTask buildProgrammingTask(Assignment assignment, ProgrammingTaskDetails details) {
        validateProgrammingTask(details);

        ProgrammingTask programmingTask = ProgrammingTask.builder()
                .language(details.language())
                .testMode(TestMode.UNIT_TEST)
                .ciConfigTemplate(details.ciConfigTemplate())
                .functionSignature(details.functionSignature())
                .testFileContent(details.testFileContent())
                .build();
        programmingTask.setAssignment(assignment);
        return programmingTask;
    }

    private void updateProgrammingTask(ProgrammingTask programmingTask, ProgrammingTaskDetails details) {
        validateProgrammingTask(details);
        programmingTask.update(TestMode.UNIT_TEST, details.functionSignature(),
                details.testFileContent(), details.ciConfigTemplate());
    }

    private void validateProgrammingTask(ProgrammingTaskDetails details) {
        if (details.functionSignature() == null || details.functionSignature().isBlank()) {
            throw new IllegalArgumentException("Function signature is required for programming tasks");
        }
        if (details.language() != Language.CPP) {
            throw new IllegalArgumentException("Unit test mode is only supported for C++");
        }
        if (details.testFileContent() == null || details.testFileContent().isBlank()) {
            throw new IllegalArgumentException("Test file content is required for unit test mode");
        }
    }

    private Course findCourseOrThrow(Long courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Course not found with id: " + courseId));
    }

    private OffsetDateTime toOffsetDateTime(LocalDateTime ldt) {
        if (ldt == null) return null;
        return ldt.atOffset(ZoneOffset.UTC);
    }
}
