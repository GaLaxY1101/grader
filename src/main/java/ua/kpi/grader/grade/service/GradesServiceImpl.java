package ua.kpi.grader.grade.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.kpi.grader.common.exception.ResourceNotFoundException;
import ua.kpi.grader.course.dto.CourseGradesResponse;
import ua.kpi.grader.course.dto.CourseGradesResponse.AssignmentGradeSummary;
import ua.kpi.grader.course.dto.CourseGradesResponse.StudentGradeCell;
import ua.kpi.grader.course.dto.CourseGradesResponse.StudentGradesRow;
import ua.kpi.grader.course.entity.Assignment;
import ua.kpi.grader.course.entity.Course;
import ua.kpi.grader.course.entity.CourseEnrollment;
import ua.kpi.grader.course.repository.AssignmentRepository;
import ua.kpi.grader.course.repository.CourseEnrollmentRepository;
import ua.kpi.grader.course.repository.CourseRepository;
import ua.kpi.grader.submission.entity.Submission;
import ua.kpi.grader.submission.repository.SubmissionRepository;
import ua.kpi.grader.user.entity.Student;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GradesServiceImpl implements GradesService {

    private final CourseRepository courseRepository;
    private final CourseEnrollmentRepository enrollmentRepository;
    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;
    private final GradesXlsxWriter xlsxWriter;

    @Override
    @Transactional(readOnly = true)
    public CourseGradesResponse getCourseGrades(Long courseId) {
        return buildGradebook(courseId);
    }

    @Override
    @Transactional(readOnly = true)
    public ExportedGradebook exportCourseGradesXlsx(Long courseId) {
        CourseGradesResponse gradebook = buildGradebook(courseId);
        String filename = "grades-" + safeName(gradebook.courseName(), courseId) + ".xlsx";
        return new ExportedGradebook(filename, xlsxWriter.write(gradebook));
    }

    private static String safeName(String courseName, Long courseId) {
        String base = courseName == null ? "" : courseName;
        String cleaned = base.replaceAll("[<>:\"/\\\\|?*]+", "").trim().replaceAll("\\s+", "-");
        return cleaned.isEmpty() ? "course-" + courseId : cleaned;
    }

    private CourseGradesResponse buildGradebook(Long courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Course not found with id: " + courseId));

        List<Assignment> assignments = assignmentRepository.findAllByCourseIdAndIsActiveTrue(courseId).stream()
                .sorted(Comparator.comparing(Assignment::getId))
                .toList();

        List<AssignmentGradeSummary> assignmentSummaries = assignments.stream()
                .map(a -> new AssignmentGradeSummary(a.getId(), a.getTitle(), a.getMaxScore()))
                .toList();

        int maxTotal = assignments.stream()
                .mapToInt(a -> a.getMaxScore() == null ? 0 : a.getMaxScore())
                .sum();

        List<CourseEnrollment> enrollments = enrollmentRepository.findAllByCourseIdWithStudentUser(courseId);
        List<Submission> submissions = submissionRepository.findAllByCourseId(courseId);

        Map<Long, Map<Long, Submission>> byStudentThenAssignment = new HashMap<>();
        for (Submission s : submissions) {
            byStudentThenAssignment
                    .computeIfAbsent(s.getStudent().getId(), k -> new HashMap<>())
                    .put(s.getAssignment().getId(), s);
        }

        List<StudentGradesRow> rows = enrollments.stream()
                .sorted(Comparator
                        .comparing((CourseEnrollment ce) -> ce.getStudent().getUser().getLastName(),
                                Comparator.nullsLast(String::compareToIgnoreCase))
                        .thenComparing(ce -> ce.getStudent().getUser().getFirstName(),
                                Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(ce -> buildRow(ce.getStudent(),
                        byStudentThenAssignment.getOrDefault(ce.getStudent().getId(), Map.of()),
                        assignments,
                        maxTotal))
                .toList();

        return new CourseGradesResponse(course.getId(), course.getName(), assignmentSummaries, rows);
    }

    private StudentGradesRow buildRow(Student student,
                                      Map<Long, Submission> submissionsByAssignment,
                                      List<Assignment> assignments,
                                      int maxTotal) {
        List<StudentGradeCell> cells = assignments.stream()
                .map(a -> {
                    Submission s = submissionsByAssignment.get(a.getId());
                    if (s == null) {
                        return new StudentGradeCell(a.getId(), null, null);
                    }
                    return new StudentGradeCell(a.getId(), s.getGrade(), s.getStatus());
                })
                .toList();

        int total = cells.stream()
                .filter(c -> c.grade() != null)
                .mapToInt(StudentGradeCell::grade)
                .sum();

        return new StudentGradesRow(
                student.getId(),
                student.getUser().getEmail(),
                student.getUser().getFirstName(),
                student.getUser().getLastName(),
                cells,
                total,
                maxTotal
        );
    }

}
