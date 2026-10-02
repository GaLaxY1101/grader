package ua.kpi.grader.testgen.mutation;

import org.junit.jupiter.api.Test;
import ua.kpi.grader.course.entity.Language;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MutatorTest {

    private final Mutator mutator = new Mutator();

    private List<String> sources(String source, Language language) {
        return mutator.generate(source, language, 1, 1000).stream().map(Mutant::source).toList();
    }

    @Test
    void relational_isMutatedBothWaysInPython() {
        String src = "def lt(a, b):\n    return a < b\n\ndef ge(a, b):\n    return a >= b\n";

        assertThat(sources(src, Language.PYTHON))
                .contains(src.replace("a < b", "a <= b"))
                .contains(src.replace("a >= b", "a > b"));
    }

    @Test
    void equality_isNegatedInCpp() {
        String src = "bool eq(int a, int b) { return a == b; }\n";

        assertThat(sources(src, Language.CPP)).contains(src.replace("==", "!="));
    }

    @Test
    void arithmetic_swapsOperators() {
        String src = "int f(int a, int b) { return a + b * 2; }\n";

        assertThat(sources(src, Language.CPP))
                .contains("int f(int a, int b) { return a - b * 2; }\n")
                .contains("int f(int a, int b) { return a + b / 2; }\n");
    }

    @Test
    void arithmetic_skipsIncrementPowerArrowAndExponent() {
        String cpp = "int f(Node* n) { int i = 0; i++; double e = 1e-5; return n->v; }\n";
        String py = "def f(x) -> int:\n    return x ** 2\n";

        assertThat(mutator.generate(cpp, Language.CPP, 1, 1000))
                .filteredOn(m -> m.operator() == MutationOperator.ARITHMETIC)
                .map(Mutant::source)
                .noneMatch(s -> s.contains("i+-") || s.contains("i-+") || s.contains("1e+5") || s.contains("n+>"));
        assertThat(mutator.generate(py, Language.PYTHON, 1, 1000))
                .filteredOn(m -> m.operator() == MutationOperator.ARITHMETIC || m.operator() == MutationOperator.RELATIONAL)
                .isEmpty();
    }

    @Test
    void constant_isChangedByPlusMinusOne() {
        String src = "def big(x):\n    return x > 10\n";

        assertThat(sources(src, Language.PYTHON))
                .contains(src.replace("10", "11"))
                .contains(src.replace("10", "9"));
    }

    @Test
    void boolean_operatorsAndLiteralsAreSwapped() {
        String cpp = "bool f(bool a, bool b) { return a && b || true; }\n";
        String py = "def f(a, b):\n    return a and b or True\n";

        assertThat(sources(cpp, Language.CPP))
                .contains(cpp.replace("a && b", "a || b"))
                .contains(cpp.replace("true", "false"));
        assertThat(sources(py, Language.PYTHON))
                .contains(py.replace("a and b", "a or b"))
                .contains(py.replace("True", "False"));
    }

    @Test
    void returnValue_isReplacedWithZero() {
        String cpp = "int sq(int x) { return x * x; }\n";
        String py = "def sq(x):\n    return x * x  # square\n";

        assertThat(sources(cpp, Language.CPP)).contains("int sq(int x) { return 0; }\n");
        assertThat(sources(py, Language.PYTHON)).contains("def sq(x):\n    return 0  # square\n");
    }

    @Test
    void commentsStringsAndIncludesAreNotMutated() {
        String src = """
                #include <vector>
                // a < b + 1
                /* x == y */
                std::string s = "a < b + 1";
                int f(int a) { return a; }
                """;

        List<Mutant> mutants = mutator.generate(src, Language.CPP, 1, 1000);

        assertThat(mutants).isNotEmpty();
        assertThat(mutants).allSatisfy(m -> {
            assertThat(m.source()).contains("#include <vector>", "// a < b + 1", "/* x == y */", "\"a < b + 1\"");
            assertThat(m.lineNo()).isEqualTo(5);
        });
    }

    @Test
    void pythonStringsAndDocstringsAreNotMutated() {
        String src = "def f(a):\n    \"\"\"Return a + 1 if a > 0.\"\"\"\n    s = 'x < y'\n    return a\n";

        assertThat(mutator.generate(src, Language.PYTHON, 1, 1000))
                .allSatisfy(m -> assertThat(m.source()).contains("Return a + 1 if a > 0.", "'x < y'"));
    }

    @Test
    void generation_isDeterministicPerSeedAndRespectsMaxCount() {
        String src = "int f(int a, int b) {\n    if (a < b && b > 0) return a + b * 2;\n    return a - 1;\n}\n";

        List<String> first = sources(src, Language.CPP);
        List<Mutant> limited = mutator.generate(src, Language.CPP, 7, 3);

        assertThat(sources(src, Language.CPP)).isEqualTo(first);
        assertThat(limited).hasSize(3);
        assertThat(limited).extracting(Mutant::id).containsExactly("M1", "M2", "M3");
        assertThat(mutator.generate(src, Language.CPP, 7, 3)).isEqualTo(limited);
        assertThat(first).doesNotHaveDuplicates().doesNotContain(src);
    }

    @Test
    void diff_showsChangedLineWithContext() {
        String src = "def f(a, b):\n    return a < b\n";

        Mutant mutant = mutator.generate(src, Language.PYTHON, 1, 1000).stream()
                .filter(m -> m.source().contains("a <= b"))
                .findFirst().orElseThrow();

        assertThat(mutant.lineNo()).isEqualTo(2);
        assertThat(mutant.diff()).contains(
                "--- solution.py (correct)",
                "+++ solution.py (buggy)",
                " def f(a, b):",
                "-    return a < b",
                "+    return a <= b");
    }
}
