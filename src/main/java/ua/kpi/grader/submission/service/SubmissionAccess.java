package ua.kpi.grader.submission.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ua.kpi.grader.course.entity.Assignment;
import ua.kpi.grader.submission.entity.Submission;
import ua.kpi.grader.submission.repository.SubmissionRepository;
import ua.kpi.grader.user.entity.Student;

/**
 * Shared get-or-create helper for a {@link Submission} identified by
 * {@code (assignment, student)}. Used by both the code (Attempts) and
 * file (state-machine) flows so the unique constraint contract is honoured
 * from a single place.
 */
@Component
@RequiredArgsConstructor
public class SubmissionAccess {

    private final SubmissionRepository submissionRepository;

    /**
     * Returns the existing submission for the pair, or persists and returns a fresh one.
     */
    public Submission getOrCreate(Assignment assignment, Student student) {
        return submissionRepository
                .findByAssignmentIdAndStudentId(assignment.getId(), student.getId())
                .orElseGet(() -> submissionRepository.save(
                        Submission.builder()
                                .assignment(assignment)
                                .student(student)
                                .build()));
    }
}
