package ua.kpi.grader.testgen.mutation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ua.kpi.grader.course.entity.Language;
import ua.kpi.grader.testgen.sandbox.SandboxRunner;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the mutant pool for one generation run: generates candidates, drops those that do
 * not compile/parse, and splits the rest into feedback set A and held-out set B.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MutantPoolBuilder {

    /** Candidates generated per requested mutant, to have spares for ones that fail to compile. */
    private static final int OVERSAMPLING = 3;

    private final Mutator mutator;
    private final SandboxRunner sandboxRunner;

    /**
     * Builds a pool of at most {@code maxMutants} valid mutants. The order of candidates and
     * thus the A/B split depend only on {@code seed}: the first half (rounded up) goes to the
     * feedback set A, the rest to the held-out set B.
     *
     * @param referenceSolution correct solution to mutate
     * @param language          solution language
     * @param seed              seed for candidate order and split
     * @param maxMutants        maximum pool size (A + B)
     * @return the pool; empty if {@code maxMutants <= 0} or no valid mutant exists
     */
    public MutantPool build(String referenceSolution, Language language, long seed, int maxMutants) {
        if (maxMutants <= 0) {
            return MutantPool.empty();
        }
        List<Mutant> candidates = mutator.generate(referenceSolution, language, seed, maxMutants * OVERSAMPLING);
        List<Boolean> valid = sandboxRunner.checkSyntax(language,
                candidates.stream().map(Mutant::source).toList());

        List<Mutant> accepted = new ArrayList<>();
        for (int i = 0; i < candidates.size() && accepted.size() < maxMutants; i++) {
            if (Boolean.TRUE.equals(valid.get(i))) {
                accepted.add(candidates.get(i));
            }
        }
        int feedbackSize = (accepted.size() + 1) / 2;
        log.debug("Mutant pool: {} candidates, {} valid, A={} B={}", candidates.size(), accepted.size(),
                feedbackSize, accepted.size() - feedbackSize);
        return new MutantPool(
                List.copyOf(accepted.subList(0, feedbackSize)),
                List.copyOf(accepted.subList(feedbackSize, accepted.size())));
    }
}
