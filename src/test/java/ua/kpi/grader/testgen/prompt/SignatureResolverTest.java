package ua.kpi.grader.testgen.prompt;

import org.junit.jupiter.api.Test;
import ua.kpi.grader.course.entity.Language;

import static org.assertj.core.api.Assertions.assertThat;

class SignatureResolverTest {

    private static final String CPP_PALINDROME = """
            #include <string>

            bool isPalindrome(const std::string& str) {
                if (str.empty()) {
                    return true;
                }
                return false;
            }
            """;

    @Test
    void resolve_cpp_wrongDeclaredParameterType_usesReferenceHeader() {
        String signature = SignatureResolver.resolve(Language.CPP, "bool isPalindrome(int x) {\n\n}", CPP_PALINDROME);

        assertThat(signature).isEqualTo("bool isPalindrome(const std::string& str);");
    }

    @Test
    void resolve_cpp_blankDeclared_usesFirstFunction() {
        assertThat(SignatureResolver.resolve(Language.CPP, "", CPP_PALINDROME))
                .isEqualTo("bool isPalindrome(const std::string& str);");
    }

    @Test
    void resolve_cpp_picksNamedFunctionNotHelper() {
        String source = """
                static int helper(int a) {
                    return a;
                }

                std::vector<int> sortDesc(std::vector<int> v) {
                    return v;
                }
                """;

        assertThat(SignatureResolver.resolve(Language.CPP, "std::vector<int> sortDesc(...)", source))
                .isEqualTo("std::vector<int> sortDesc(std::vector<int> v);");
    }

    @Test
    void resolve_python_usesReferenceDef() {
        String source = """
                def fib(n: int) -> int:
                    if n < 2:
                        return n
                    return fib(n - 1) + fib(n - 2)
                """;

        assertThat(SignatureResolver.resolve(Language.PYTHON, "def fib(x):", source))
                .isEqualTo("def fib(n: int) -> int:");
    }

    @Test
    void resolve_functionNotInReference_keepsDeclared() {
        assertThat(SignatureResolver.resolve(Language.CPP, "int add(int a, int b);", CPP_PALINDROME))
                .isEqualTo("int add(int a, int b);");
    }
}
