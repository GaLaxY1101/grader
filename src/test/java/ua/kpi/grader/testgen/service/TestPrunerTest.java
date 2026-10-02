package ua.kpi.grader.testgen.service;

import org.junit.jupiter.api.Test;
import ua.kpi.grader.course.entity.Language;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TestPrunerTest {

    @Test
    void python_removesFunctionWithDecoratorAndBody() {
        String tests = """
                import pytest
                from solution import *


                def test_ok():
                    assert fib(1) == 1


                @pytest.mark.parametrize("n", [1, 2])
                def test_bad(n):
                    with pytest.raises(ValueError):
                        fib(-n)


                def test_also_ok():
                    assert fib(2) == 1
                """;

        String pruned = TestPruner.remove(Language.PYTHON, tests, Set.of("test_bad[1]"));

        assertThat(pruned).contains("def test_ok", "def test_also_ok")
                .doesNotContain("test_bad", "parametrize", "raises");
    }

    @Test
    void cpp_removesFunctionAndItsCall() {
        String tests = """
                #include <iostream>
                #include "solution.cpp"
                static int failures = 0;
                void test_ok() {
                    if (rev("ab") == "ba") { std::cout << "PASS test_ok" << std::endl; }
                }
                void test_bad() {
                    if (rev("}") == "{") { std::cout << "PASS test_bad" << std::endl; }
                    else { std::cout << "FAIL test_bad: expected { got }" << std::endl; failures++; }
                }
                int main() {
                    test_ok();
                    test_bad();
                    return failures;
                }
                """;

        String pruned = TestPruner.remove(Language.CPP, tests, Set.of("test_bad"));

        assertThat(pruned).contains("void test_ok()", "    test_ok();", "return failures;")
                .doesNotContain("test_bad");
    }

    @Test
    void returnsNull_whenTestCannotBeLocated() {
        assertThat(TestPruner.remove(Language.PYTHON, "def test_a():\n    pass\n", Set.of("test_missing"))).isNull();
        assertThat(TestPruner.remove(Language.CPP, "int main() { return 0; }\n", Set.of("main"))).isNull();
    }
}
