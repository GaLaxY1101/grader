package ua.kpi.grader.testgen.mutation;

import java.util.List;

/**
 * Valid mutants of a reference solution, split into two disjoint sets.
 *
 * @param feedback set A: survivors may be shown to the LLM as feedback
 * @param heldOut  set B: never shown to the LLM; only used to measure fault detection
 */
public record MutantPool(List<Mutant> feedback, List<Mutant> heldOut) {

    public static MutantPool empty() {
        return new MutantPool(List.of(), List.of());
    }

    /**
     * Returns true if {@code text} contains the diff of any held-out mutant. Used as a guard
     * that set B never leaks into a prompt.
     */
    public boolean leaksHeldOut(String text) {
        return heldOut.stream().anyMatch(m -> text.contains(m.diff()));
    }
}
