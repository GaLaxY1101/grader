package ua.kpi.grader.testgen.mutation;

/**
 * Classic mutation operators applied to the reference solution.
 */
public enum MutationOperator {
    /** {@code <}↔{@code <=}, {@code >}↔{@code >=}, {@code ==}↔{@code !=}. */
    RELATIONAL,
    /** {@code +}↔{@code -}, {@code *}↔{@code /}. */
    ARITHMETIC,
    /** Integer literal changed by ±1. */
    CONSTANT,
    /** {@code &&}↔{@code ||}, {@code and}↔{@code or}, {@code True}↔{@code False}, {@code true}↔{@code false}. */
    BOOLEAN,
    /** {@code return <expr>} replaced by {@code return 0}. */
    RETURN_VALUE
}
