package ua.kpi.grader.testgen.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ua.kpi.grader.testgen.entity.JobStatus;
import ua.kpi.grader.testgen.entity.TestGenerationJob;

import java.time.OffsetDateTime;
import java.util.Collection;

public interface TestGenerationJobRepository extends JpaRepository<TestGenerationJob, Long> {

    boolean existsByCreatedByAndStatusIn(String createdBy, Collection<JobStatus> statuses);

    @Modifying
    @Query("""
            UPDATE TestGenerationJob j
               SET j.status = ua.kpi.grader.testgen.entity.JobStatus.FAILED,
                   j.errorMessage = :message,
                   j.finishedAt = :now
             WHERE j.status IN :statuses
            """)
    int failAllWithStatus(@Param("statuses") Collection<JobStatus> statuses,
                          @Param("message") String message,
                          @Param("now") OffsetDateTime now);
}
