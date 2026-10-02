package ua.kpi.grader.testgen.mutation;

/**
 * A copy of the reference solution with exactly one small change.
 *
 * @param id       stable id within one generation run, e.g. {@code M3}
 * @param operator operator that produced the change
 * @param lineNo   1-based line of the change
 * @param source   full mutated source
 * @param diff     unified diff from the reference solution to this mutant
 */
public record Mutant(
        String id,
        MutationOperator operator,
        int lineNo,
        String source,
        String diff
) {}
