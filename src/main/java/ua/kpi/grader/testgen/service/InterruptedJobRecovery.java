package ua.kpi.grader.testgen.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * On startup, fails jobs that were still running when the previous process stopped.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InterruptedJobRecovery {

    private final TestGenerationService testGenerationService;

    @EventListener(ApplicationReadyEvent.class)
    public void failInterruptedJobs() {
        int count = testGenerationService.failInterruptedJobs();
        if (count > 0) {
            log.warn("Marked {} interrupted test generation job(s) as FAILED", count);
        }
    }
}
