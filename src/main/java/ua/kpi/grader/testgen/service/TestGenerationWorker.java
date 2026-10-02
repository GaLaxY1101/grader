package ua.kpi.grader.testgen.service;

import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.stereotype.Component;

/**
 * Runs generation jobs in the background on virtual threads. Concurrency is capped because
 * every job keeps the LLM and Docker busy; extra jobs wait for a free slot.
 */
@Component
public class TestGenerationWorker {

    private static final int MAX_CONCURRENT_JOBS = 2;

    private final SimpleAsyncTaskExecutor executor;

    public TestGenerationWorker() {
        this.executor = new SimpleAsyncTaskExecutor("testgen-");
        this.executor.setVirtualThreads(true);
        this.executor.setConcurrencyLimit(MAX_CONCURRENT_JOBS);
    }

    /**
     * Schedules {@code task} for background execution.
     */
    public void submit(Runnable task) {
        executor.execute(task);
    }
}
