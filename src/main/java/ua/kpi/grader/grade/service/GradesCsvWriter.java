package ua.kpi.grader.grade.service;

import org.springframework.stereotype.Component;
import ua.kpi.grader.course.dto.CourseGradesResponse;
import ua.kpi.grader.course.dto.CourseGradesResponse.AssignmentGradeSummary;
import ua.kpi.grader.course.dto.CourseGradesResponse.StudentGradeCell;
import ua.kpi.grader.course.dto.CourseGradesResponse.StudentGradesRow;

import java.nio.charset.StandardCharsets;

@Component
class GradesCsvWriter {

    /**
     * Serializes the gradebook as CSV. Header row: student columns + one column per
     * assignment + Total. Cells are quoted only when they contain a comma or a quote.
     */
    byte[] write(CourseGradesResponse gradebook) {
        StringBuilder out = new StringBuilder(1024);

        out.append("Last name,First name,Email");
        for (AssignmentGradeSummary a : gradebook.assignments()) {
            out.append(',').append(quote(a.title() + " (/" + a.maxScore() + ")"));
        }
        out.append(",Total\n");

        for (StudentGradesRow row : gradebook.students()) {
            out.append(quote(nullToEmpty(row.lastName()))).append(',')
                    .append(quote(nullToEmpty(row.firstName()))).append(',')
                    .append(quote(nullToEmpty(row.email())));
            for (StudentGradeCell cell : row.grades()) {
                out.append(',').append(formatGrade(cell));
            }
            out.append(',').append(row.total()).append('/').append(row.maxTotal()).append('\n');
        }

        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String formatGrade(StudentGradeCell cell) {
        if (cell.grade() != null) return String.valueOf(cell.grade());
        if (cell.status() == null) return "";
        return "(" + cell.status() + ")";
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String quote(String v) {
        if (v.indexOf(',') < 0 && v.indexOf('"') < 0 && v.indexOf('\n') < 0) {
            return v;
        }
        return '"' + v.replace("\"", "\"\"") + '"';
    }
}
