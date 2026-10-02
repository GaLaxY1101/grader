package ua.kpi.grader.testgen.prompt;

import ua.kpi.grader.course.entity.Language;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Derives the function signature shown to the LLM from the reference solution.
 *
 * <p>The signature typed by the teacher can drift from the reference solution (e.g. a template
 * left at {@code bool isPalindrome(int x)} while the solution takes {@code const std::string&}).
 * Tests written against the wrong signature never compile, and no amount of repair fixes that,
 * because every repair prompt repeats the wrong signature. Only the header of the function is
 * taken from the reference; its body is never shown to the model.
 */
public final class SignatureResolver {

    private static final Pattern PY_DEF = Pattern.compile(
            "^def\\s+(\\w+)\\s*\\([^)]*\\)\\s*(?:->\\s*[^:]+)?:", Pattern.MULTILINE);
    /** Unindented C/C++ function definition: return type, name, parameters, optional const, then '{'. */
    private static final Pattern C_DEF = Pattern.compile(
            "^(?!\\s)(?!(?:if|for|while|switch|return|else|do)\\b)([\\w:<>,*&\\s]+?\\b(\\w+)\\s*\\([^;{}()]*\\)(?:\\s*const)?)\\s*\\{",
            Pattern.MULTILINE);
    private static final Pattern PY_NAME = Pattern.compile("def\\s+(\\w+)");
    private static final Pattern C_NAME = Pattern.compile("(\\w+)\\s*\\(");

    private SignatureResolver() {
    }

    /**
     * Returns the reference solution's header of the function named in {@code declared}
     * (or of its first function when {@code declared} is blank). Falls back to {@code declared}
     * when the reference has no matching function.
     */
    public static String resolve(Language language, String declared, String referenceSolution) {
        if (referenceSolution == null || referenceSolution.isBlank()) {
            return declared;
        }
        String name = declared == null ? null : functionName(language, declared).orElse(null);
        return referenceHeader(language, referenceSolution, name).orElse(declared);
    }

    static Optional<String> functionName(Language language, String signature) {
        Matcher m = (language == Language.PYTHON ? PY_NAME : C_NAME).matcher(signature);
        return m.find() ? Optional.of(m.group(1)) : Optional.empty();
    }

    static Optional<String> referenceHeader(Language language, String source, String name) {
        boolean python = language == Language.PYTHON;
        Matcher m = (python ? PY_DEF : C_DEF).matcher(source);
        while (m.find()) {
            String found = m.group(python ? 1 : 2);
            if (name == null || name.equals(found)) {
                String header = (python ? m.group() : m.group(1)).strip().replaceAll("\\s+", " ");
                return Optional.of(python ? header : header + ";");
            }
        }
        return Optional.empty();
    }
}
