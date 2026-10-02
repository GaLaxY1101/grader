package ua.kpi.grader.testgen.service;

import ua.kpi.grader.testgen.dto.StartTestGenerationRequest;
import ua.kpi.grader.testgen.dto.TestGenerationJobResponse;

public interface TestGenerationService {

    /**
     * Validates the request, stores a PENDING job and starts the self-repair loop in the
     * background.
     *
     * @return id of the new job
     */
    Long startJob(StartTestGenerationRequest request);

    /**
     * Returns a job with its iterations, if the current user may see it.
     */
    TestGenerationJobResponse getJob(Long jobId);

    /**
     * Runs the full generation loop synchronously without touching the database.
     * Same code path as background jobs; used by the evaluation.
     */
    GenerationOutcome runSync(GenerationRequest request, GenerationConfig config);

    /**
     * Returns the default configuration from {@code testgen.*}.
     */
    GenerationConfig defaultConfig();

    /**
     * Marks jobs left PENDING/RUNNING by a previous process (e.g. a restart) as FAILED, so they
     * do not block their owners through the one-running-job limit.
     *
     * @return number of jobs updated
     */
    int failInterruptedJobs();
}
