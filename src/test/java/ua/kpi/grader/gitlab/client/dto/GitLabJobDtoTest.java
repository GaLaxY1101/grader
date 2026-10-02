package ua.kpi.grader.gitlab.client.dto;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GitLabJobDtoTest {

    @Test
    void deserializesGitLabJobWithStringStage() throws Exception {
        // Shape of GET /projects/:id/pipelines/:pipeline_id/jobs (trimmed); "stage" is the stage name.
        String json = """
                [{"id": 124, "name": "test", "status": "failed", "stage": "test", "ref": "main"}]
                """;
        ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        GitLabJobDto[] jobs = mapper.readValue(json, GitLabJobDto[].class);

        assertThat(jobs).singleElement().satisfies(job -> {
            assertThat(job.id()).isEqualTo(124);
            assertThat(job.stage()).isEqualTo("test");
        });
    }
}
