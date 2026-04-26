package ua.kpi.grader;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import ua.kpi.grader.gitlab.config.GitLabProperties;

import java.util.TimeZone;

@SpringBootApplication
@EnableConfigurationProperties(GitLabProperties.class)
public class GraderApplication {

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Kyiv"));
        SpringApplication.run(GraderApplication.class, args);
    }
}
