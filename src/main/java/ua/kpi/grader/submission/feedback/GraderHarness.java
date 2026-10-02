package ua.kpi.grader.submission.feedback;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import ua.kpi.grader.course.entity.Language;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Grader test harness files committed next to the teacher's test file on every attempt and
 * copied into the AI test-generation sandbox. The harness makes the test run emit
 * {@code ##GRADER##} result events that {@link TestReportParser} turns into per-test results.
 *
 * <p>C and C++ share {@code grader_test.h} (both are compiled as {@code test.cpp} with g++);
 * Python uses a pytest plugin in {@code conftest.py}.
 */
@Component
public class GraderHarness {

    public static final String CPP_FILE_NAME = "grader_test.h";
    public static final String PYTHON_FILE_NAME = "conftest.py";

    private final String cppHeader;
    private final String pythonPlugin;

    /**
     * Loads both harness files from the classpath once.
     */
    public GraderHarness() {
        this.cppHeader = load("harness/" + CPP_FILE_NAME);
        this.pythonPlugin = load("harness/" + PYTHON_FILE_NAME);
    }

    /**
     * Returns the harness file name for the given language.
     *
     * @param language task language
     * @return file name relative to the repository root
     */
    public String fileName(Language language) {
        return language == Language.PYTHON ? PYTHON_FILE_NAME : CPP_FILE_NAME;
    }

    /**
     * Returns the harness file content for the given language.
     *
     * @param language task language
     * @return harness source code
     */
    public String content(Language language) {
        return language == Language.PYTHON ? pythonPlugin : cppHeader;
    }

    private static String load(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Missing grader harness: " + path, e);
        }
    }
}
