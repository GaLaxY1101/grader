package ua.kpi.grader.course.dto;

import jakarta.validation.constraints.NotNull;
import ua.kpi.grader.course.entity.FeedbackLevel;
import ua.kpi.grader.course.entity.Language;
import ua.kpi.grader.course.entity.ProgrammingTask;
import ua.kpi.grader.course.entity.TestMode;

public record ProgrammingTaskDetails(
        @NotNull Language language,
        TestMode testMode,
        String ciConfigTemplate,
        String functionSignature,
        String testFileContent,
        /** Teacher's correct solution. Optional; always null in responses for students. */
        String referenceSolution,
        /** How much per-test feedback students see. Optional in requests; defaults to FULL. */
        FeedbackLevel feedbackLevel
) {
    /**
     * Maps a programming task to its DTO.
     *
     * @param includeReferenceSolution whether the caller may see the reference solution
     *                                 (TEACHER/ADMIN only); when false it is set to null
     */
    public static ProgrammingTaskDetails from(ProgrammingTask task, boolean includeReferenceSolution) {
        if (task == null) return null;
        return new ProgrammingTaskDetails(
                task.getLanguage(),
                task.getTestMode(),
                task.getCiConfigTemplate(),
                task.getFunctionSignature(),
                task.getTestFileContent(),
                includeReferenceSolution ? task.getReferenceSolution() : null,
                task.getFeedbackLevel()
        );
    }
}
