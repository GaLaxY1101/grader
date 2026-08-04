package ua.kpi.grader.gitlab.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
public class CiConfigService {

    private final String unitTestTemplate;

    /**
     * Loads the unit-test CI template from classpath at construction time.
     */
    public CiConfigService() {
        this.unitTestTemplate = loadClasspathResource("ci-templates/cpp-unittest.yml");
        log.info("Loaded CI template for UNIT_TEST mode: ci-templates/cpp-unittest.yml");
    }

    /**
     * Generate .gitlab-ci.yml content. Returns the teacher-provided custom
     * template if present; otherwise returns the default unit-test template.
     *
     * @param customTemplate teacher-provided CI YAML, or null/blank to use the default
     * @return .gitlab-ci.yml content as a string
     */
    public String generateCiConfig(String customTemplate) {
        if (customTemplate != null && !customTemplate.isBlank()) {
            return customTemplate;
        }
        return unitTestTemplate;
    }

    private static String loadClasspathResource(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Missing CI template: " + path, e);
        }
    }
}
