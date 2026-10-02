package ua.kpi.grader.testgen.mutation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ua.kpi.grader.course.entity.Language;
import ua.kpi.grader.testgen.sandbox.SandboxRunner;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MutantPoolBuilderTest {

    @Mock
    private Mutator mutator;

    @Mock
    private SandboxRunner sandboxRunner;

    @InjectMocks
    private MutantPoolBuilder builder;

    @Test
    void build_dropsInvalidMutantsAndSplitsInOrder() {
        List<Mutant> candidates = IntStream.rangeClosed(1, 6)
                .mapToObj(i -> new Mutant("M" + i, MutationOperator.CONSTANT, 1, "src" + i, "diff" + i))
                .toList();
        when(mutator.generate("ref", Language.PYTHON, 3L, 12)).thenReturn(candidates);
        when(sandboxRunner.checkSyntax(eq(Language.PYTHON), anyList()))
                .thenReturn(List.of(true, false, true, true, false, true));

        MutantPool pool = builder.build("ref", Language.PYTHON, 3L, 4);

        assertThat(pool.feedback()).extracting(Mutant::id).containsExactly("M1", "M3");
        assertThat(pool.heldOut()).extracting(Mutant::id).containsExactly("M4", "M6");
    }

    @Test
    void build_returnsEmptyPoolWhenNoMutantsRequested() {
        MutantPool pool = builder.build("ref", Language.CPP, 1L, 0);

        assertThat(pool.feedback()).isEmpty();
        assertThat(pool.heldOut()).isEmpty();
        verifyNoInteractions(mutator, sandboxRunner);
    }

    @Test
    void leaksHeldOut_detectsHeldOutDiffInText() {
        Mutant a = new Mutant("M1", MutationOperator.RELATIONAL, 1, "a", "-x < y\n+x <= y\n");
        Mutant b = new Mutant("M2", MutationOperator.RELATIONAL, 1, "b", "-x > y\n+x >= y\n");
        MutantPool pool = new MutantPool(List.of(a), List.of(b));

        assertThat(pool.leaksHeldOut("prompt with " + a.diff())).isFalse();
        assertThat(pool.leaksHeldOut("prompt with " + b.diff())).isTrue();
    }
}
