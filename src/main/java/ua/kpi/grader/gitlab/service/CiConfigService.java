package ua.kpi.grader.gitlab.service;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CiConfigService {

    /**
     * Generate .gitlab-ci.yml content for the given language.
     * Returns the teacher-provided custom template if present;
     * otherwise falls back to the built-in default template for that language.
     *
     * @param language       programming language (e.g. "c", "c++")
     * @param customTemplate teacher-provided CI YAML, or null/blank to use the default
     * @param testCommands   list of shell commands to run in the test stage
     * @return .gitlab-ci.yml content as a string
     */
    public String generateCiConfig(String language, String customTemplate, List<String> testCommands) {
        if (customTemplate != null && !customTemplate.isBlank()) {
            return customTemplate;
        }
        return getDefaultTemplate(language, testCommands);
    }

    private String getDefaultTemplate(String language, List<String> testCommands) {
        return switch (language.toLowerCase()) {
            case "c", "c++" -> buildCppTemplate(testCommands);
            default -> throw new IllegalArgumentException("Unsupported language: " + language);
        };
    }

    private String buildCppTemplate(List<String> testCommands) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
                image: gcc:latest

                stages:
                  - compile
                  - test

                compile:
                  stage: compile
                  script:
                    - gcc -o solution solution.c || g++ -o solution solution.cpp
                  artifacts:
                    paths:
                      - solution

                """);

        if (testCommands != null && !testCommands.isEmpty()) {
            sb.append("test:\n");
            sb.append("  stage: test\n");
            sb.append("  script:\n");
            testCommands.forEach(cmd -> sb.append("    - ").append(cmd).append("\n"));
        }

        return sb.toString();
    }
}
