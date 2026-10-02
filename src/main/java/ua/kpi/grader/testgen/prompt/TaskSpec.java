package ua.kpi.grader.testgen.prompt;

import ua.kpi.grader.course.entity.Language;

/**
 * What the LLM is told about the task: language, statement and function signature.
 * Deliberately excludes the reference solution.
 */
public record TaskSpec(Language language, String description, String signature) {}
