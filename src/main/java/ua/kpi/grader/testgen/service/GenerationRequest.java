package ua.kpi.grader.testgen.service;

import ua.kpi.grader.course.entity.Language;

/**
 * Input of one test generation run.
 *
 * @param language          solution and test language
 * @param taskDescription   task statement shown to students
 * @param functionSignature signature of the function under test
 * @param referenceSolution correct solution used to validate the tests (never shown to the LLM
 *                          except as mutant diffs from the feedback set)
 */
public record GenerationRequest(
        Language language,
        String taskDescription,
        String functionSignature,
        String referenceSolution
) {}
