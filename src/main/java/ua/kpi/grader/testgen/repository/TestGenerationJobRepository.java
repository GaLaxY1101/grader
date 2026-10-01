package ua.kpi.grader.testgen.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ua.kpi.grader.testgen.entity.JobStatus;
import ua.kpi.grader.testgen.entity.TestGenerationJob;

import java.util.Collection;

public interface TestGenerationJobRepository extends JpaRepository<TestGenerationJob, Long> {

    boolean existsByCreatedByAndStatusIn(String createdBy, Collection<JobStatus> statuses);
}
