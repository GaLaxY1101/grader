package ua.kpi.grader.testgen.prompt;

import org.junit.jupiter.api.Test;
import ua.kpi.grader.course.entity.Language;
import ua.kpi.grader.testgen.sandbox.TestFailure;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromptBuilderTest {

    private static final TaskSpec FIB = new TaskSpec(Language.PYTHON, "Return the n-th Fibonacci number.", "def fib(n):");

    private final PromptBuilder builder = new PromptBuilder();

    @Test
    void generate_python_containsSpecAndConventions() {
        String prompt = builder.generate(FIB, 5);

        assertThat(prompt).contains("Return the n-th Fibonacci number.", "def fib(n):", "at least 5",
                "from solution import *", "def test_");
        assertThat(prompt).doesNotContain("{{");
    }

    @Test
    void generate_c_includesSolutionDotC() {
        String prompt = builder.generate(new TaskSpec(Language.C, "Sum two ints.", "int add(int a, int b);"), 5);

        assertThat(prompt).contains("#include \"solution.c\"", "#include \"grader_test.h\"", "TEST_CASE(test_","```c\n");
    }

    @Test
    void repairPrompts_areSelfContainedWithTaskSummary() {
        String compile = builder.repairCompile(FIB, "def test_a(): pass", "SyntaxError");
        String cpp = builder.killMutant(new TaskSpec(Language.CPP, "Reverse.", "std::string rev(std::string);"),
                "int main() {}", "-a\n+b");

        assertThat(compile).contains("## Task", "Return the n-th Fibonacci number.", "def fib(n):",
                "from solution import *", "SyntaxError", "def test_a(): pass");
        assertThat(cpp).contains("Reverse.", "#include", "solution.cpp", "TEST_CASE(test_<name>)");
    }

    @Test
    void repairFailing_listsFailuresAndProblem() {
        String prompt = builder.repairFailing(FIB, "def test_a(): pass",
                List.of(new TestFailure("test_a", "assert 1 == 2")), "Tests timed out.");

        assertThat(prompt).contains("- test_a: assert 1 == 2", "- Tests timed out.", "known to be correct");
    }

    @Test
    void killMutant_containsDiffAndTests() {
        String prompt = builder.killMutant(new TaskSpec(Language.CPP, "x", "int f();"), "int main() {}", "-a < b\n+a <= b");

        assertThat(prompt).contains("-a < b\n+a <= b", "int main() {}", "```cpp");
    }

    @Test
    void render_insertsValuesVerbatimInSinglePass() {
        String rendered = PromptBuilder.render("x={{a}} y={{b}}", Map.of("a", "{{b}} $1 \\", "b", "B"));

        assertThat(rendered).isEqualTo("x={{b}} $1 \\ y=B");
    }

    @Test
    void render_failsOnMissingValue() {
        assertThatThrownBy(() -> PromptBuilder.render("{{missing}}", Map.of()))
                .isInstanceOf(IllegalStateException.class);
    }
}
