package ua.kpi.grader.gitlab.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import ua.kpi.grader.course.entity.Language;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;

@Slf4j
@Service
public class CiConfigService {

    private static final Map<Language, String> TEMPLATE_PATHS = Map.of(
            Language.C, "ci-templates/cpp-unittest.yml",
            Language.CPP, "ci-templates/cpp-unittest.yml",
            Language.PYTHON, "ci-templates/python-unittest.yml"
    );

    private final Map<Language, String> unitTestTemplates;

    /**
     * Loads all per-language unit-test CI templates from the classpath at construction time.
     */
    public CiConfigService() {
        this.unitTestTemplates = new EnumMap<>(Language.class);
        TEMPLATE_PATHS.forEach((language, path) -> {
            unitTestTemplates.put(language, loadClasspathResource(path));
            log.info("Loaded CI template for {}: {}", language, path);
        });
    }

    /**
     * Generate .gitlab-ci.yml content for the given language. Returns the
     * teacher-provided custom template if present; otherwise the default
     * unit-test template for that language.
     *
     * @param customTemplate teacher-provided CI YAML, or null/blank to use the default
     * @param language       target language for default template selection
     * @return .gitlab-ci.yml content as a string
     */
    public String generateCiConfig(String customTemplate, Language language) {
        if (customTemplate != null && !customTemplate.isBlank()) {
            return customTemplate;
        }
        String template = unitTestTemplates.get(language);
        if (template == null) {
            throw new IllegalStateException("No default CI template registered for language: " + language);
        }
        return template;
    }

    private static String loadClasspathResource(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Missing CI template: " + path, e);
        }
    }
}
