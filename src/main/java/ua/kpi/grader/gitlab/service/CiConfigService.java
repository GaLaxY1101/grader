package ua.kpi.grader.gitlab.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import ua.kpi.grader.course.dto.TestCaseDetails;
import ua.kpi.grader.course.entity.Language;
import ua.kpi.grader.course.entity.TestMode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class CiConfigService {

    private final Map<Language, String> baseTemplates;
    private final String unitTestTemplate;

    /**
     * Loads all CI base templates from classpath at construction time.
     * Each language maps to a file under ci-templates/{key}.yml.
     */
    public CiConfigService() {
        this.baseTemplates = new EnumMap<>(Language.class);
        for (Language lang : Language.values()) {
            String templateFile = resolveTemplateFile(lang);
            baseTemplates.put(lang, loadClasspathResource("ci-templates/" + templateFile));
            log.info("Loaded CI template for {}: ci-templates/{}", lang, templateFile);
        }
        this.unitTestTemplate = loadClasspathResource("ci-templates/cpp-unittest.yml");
        log.info("Loaded CI template for UNIT_TEST mode: ci-templates/cpp-unittest.yml");
    }

    /**
     * Generate .gitlab-ci.yml content for the given language and test mode.
     * Returns the teacher-provided custom template if present;
     * otherwise generates config based on test mode.
     *
     * @param language       the programming language enum value
     * @param testMode       IO for traditional I/O tests, UNIT_TEST for assertion-based tests
     * @param customTemplate teacher-provided CI YAML, or null/blank to use the default
     * @param testCases      structured test cases to compile into the test stage (IO mode only)
     * @return .gitlab-ci.yml content as a string
     */
    public String generateCiConfig(Language language, TestMode testMode,
                                   String customTemplate, List<TestCaseDetails> testCases) {
        if (customTemplate != null && !customTemplate.isBlank()) {
            return customTemplate;
        }
        return switch (testMode) {
            case IO -> buildConfig(language, testCases);
            case UNIT_TEST -> unitTestTemplate;
        };
    }

    /**
     * @deprecated Use {@link #generateCiConfig(Language, TestMode, String, List)} instead.
     */
    @Deprecated
    public String generateCiConfig(Language language, String customTemplate, List<TestCaseDetails> testCases) {
        return generateCiConfig(language, TestMode.IO, customTemplate, testCases);
    }

    private String buildConfig(Language language, List<TestCaseDetails> testCases) {
        StringBuilder sb = new StringBuilder(baseTemplates.get(language));

        if (testCases != null && !testCases.isEmpty()) {
            sb.append("\ntest:\n");
            sb.append("  stage: test\n");
            sb.append("  script:\n");
            testCases.forEach(tc -> appendTestStep(sb, tc));
        }

        return sb.toString();
    }

    private void appendTestStep(StringBuilder sb, TestCaseDetails tc) {
        String shellName = tc.name().replace("'", "'\\''");
        switch (tc.testType()) {
            case IO -> {
                String b64Input = b64(tc.input());
                String b64Expected = b64(tc.expectedOutput());
                sb.append("    - ").append(qs("echo '" + b64Input + "' | base64 -d > /tmp/input.txt")).append("\n");
                sb.append("    - ").append(qs("echo '" + b64Expected + "' | base64 -d > /tmp/expected.txt")).append("\n");
                sb.append("    - ").append(qs("./solution < /tmp/input.txt > /tmp/actual.txt")).append("\n");
                sb.append("    - ").append(qs("diff <(sed -z 's/[[:space:]]*$/\\n/' /tmp/expected.txt) <(sed -z 's/[[:space:]]*$/\\n/' /tmp/actual.txt) || (echo 'FAIL: " + shellName + "' && exit 1)")).append("\n");
            }
            case EXCEPTION -> {
                String b64Input = b64(tc.input());
                sb.append("    - ").append(qs("echo '" + b64Input + "' | base64 -d > /tmp/input.txt")).append("\n");
                sb.append("    - ").append(qs("./solution < /tmp/input.txt; [ $? -ne 0 ] || (echo 'FAIL: " + shellName + " expected non-zero exit' && exit 1)")).append("\n");
            }
        }
    }

    /**
     * Maps a Language enum to its template filename.
     * C and CPP share the same gcc-based template.
     */
    private static String resolveTemplateFile(Language language) {
        return switch (language) {
            case C, CPP -> "cpp.yml";
        };
    }

    private static String loadClasspathResource(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Missing CI template: " + path, e);
        }
    }

    /**
     * Base64-encodes a test value so it can be safely embedded in the CI script
     * regardless of newlines, quotes, or other special characters in the data.
     */
    private static String b64(String value) {
        String v = value != null ? value : "";
        return Base64.getEncoder().encodeToString(v.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Wraps a shell command in a YAML double-quoted string.
     * This prevents YAML from misinterpreting shell syntax such as ": ", ">", "|", "#".
     * Inside YAML double-quotes only "\" and "\"" need escaping.
     */
    private static String qs(String shellCmd) {
        return "\"" + shellCmd.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
