package ua.kpi.grader.testgen.prompt;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import ua.kpi.grader.course.entity.Language;
import ua.kpi.grader.testgen.sandbox.TestFailure;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Renders the LLM prompts from the plain-text templates in {@code resources/prompts/}.
 * Placeholders have the form {@code {{name}}} and are substituted in a single pass, so
 * values containing braces (e.g. code) are inserted verbatim.
 *
 * <p>Repair prompts are self-contained (task, current test file, feedback): the loop sends
 * them without chat history, because small models tend to repeat their previous answer when
 * it is in the context.
 */
@Component
public class PromptBuilder {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)}}");
    private static final int MAX_FAILURES_LISTED = 10;

    private final String system = load("system.txt");
    private final String generate = load("generate.txt");
    private final String conventionsPython = load("conventions_python.txt");
    private final String conventionsCpp = load("conventions_cpp.txt");
    private final String repairCompile = load("repair_compile.txt");
    private final String repairFailing = load("repair_failing.txt");
    private final String killMutant = load("kill_mutant.txt");

    /**
     * Returns the system prompt.
     */
    public String system() {
        return system;
    }

    /**
     * Builds the initial generation prompt. The reference solution is deliberately not included:
     * tests must be derived from the specification.
     */
    public String generate(TaskSpec task, int minTests) {
        Language language = task.language();
        String conventions = language == Language.PYTHON
                ? conventionsPython
                : render(conventionsCpp, Map.of("solutionFile", language.getSolutionFileName()));
        return render(generate, Map.of(
                "language", language.getDisplayName(),
                "description", description(task),
                "fence", signatureFence(language),
                "signature", signature(task),
                "minTests", Integer.toString(minTests),
                "conventions", conventions.strip()));
    }

    /**
     * Builds the prompt asking to fix compile or import errors.
     */
    public String repairCompile(TaskSpec task, String tests, String errors) {
        return render(repairCompile, Map.of(
                "task", taskSummary(task),
                "errors", errors.strip(),
                "fence", testFence(task.language()),
                "tests", tests.strip()));
    }

    /**
     * Builds the prompt asking to fix tests that fail on the (correct) reference solution.
     *
     * @param problem free-text description used when there are no individual failures
     *                (e.g. timeout or no tests collected); may be null
     */
    public String repairFailing(TaskSpec task, String tests, List<TestFailure> failures, String problem) {
        String listed = failures.stream()
                .limit(MAX_FAILURES_LISTED)
                .map(f -> "- " + f.name() + (f.message().isBlank() ? "" : ": " + f.message().strip()))
                .collect(Collectors.joining("\n"));
        if (failures.size() > MAX_FAILURES_LISTED) {
            listed += "\n- ... and " + (failures.size() - MAX_FAILURES_LISTED) + " more";
        }
        if (problem != null && !problem.isBlank()) {
            listed = listed.isEmpty() ? "- " + problem : listed + "\n- " + problem;
        }
        return render(repairFailing, Map.of(
                "task", taskSummary(task),
                "failures", listed,
                "fence", testFence(task.language()),
                "tests", tests.strip()));
    }

    /**
     * Builds the prompt asking for a test that detects the given surviving mutant.
     *
     * @param diff unified diff between the reference solution and the mutant (feedback set only)
     */
    public String killMutant(TaskSpec task, String tests, String diff) {
        return render(killMutant, Map.of(
                "task", taskSummary(task),
                "diff", diff.strip(),
                "fence", testFence(task.language()),
                "tests", tests.strip()));
    }

    /** Task statement, signature and a one-line reminder of the test file conventions. */
    private static String taskSummary(TaskSpec task) {
        Language language = task.language();
        String conventions = language == Language.PYTHON
                ? "pytest file `test_solution.py` that starts with `from solution import *`."
                : "`test.cpp` that does `#include \"" + language.getSolutionFileName() + "\"`, has one `void test_<name>()` "
                        + "function per case printing `PASS test_<name>` or `FAIL test_<name>: expected <x> got <y>`, "
                        + "and a `main()` returning the number of failures.";
        return description(task) + "\n\nFunction under test:\n```" + signatureFence(language) + "\n"
                + signature(task) + "\n```\n\nTest file: " + conventions;
    }

    static String render(String template, Map<String, String> values) {
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String value = values.get(m.group(1));
            if (value == null) {
                throw new IllegalStateException("Missing prompt placeholder value: " + m.group(1));
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String description(TaskSpec task) {
        return orPlaceholder(task.description(), "(no description provided)");
    }

    private static String signature(TaskSpec task) {
        return orPlaceholder(task.signature(), "(see task description)");
    }

    private static String testFence(Language language) {
        return language == Language.PYTHON ? "python" : "cpp";
    }

    private static String signatureFence(Language language) {
        return switch (language) {
            case PYTHON -> "python";
            case C -> "c";
            case CPP -> "cpp";
        };
    }

    private static String orPlaceholder(String value, String placeholder) {
        return value == null || value.isBlank() ? placeholder : value.strip();
    }

    private static String load(String name) {
        try (InputStream in = new ClassPathResource("prompts/" + name).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new UncheckedIOException("Prompt template not found: " + name, e);
        }
    }
}
