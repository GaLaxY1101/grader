package ua.kpi.grader.testgen.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ua.kpi.grader.testgen.entity.TestGenerationIteration;

import java.util.List;

public interface TestGenerationIterationRepository extends JpaRepository<TestGenerationIteration, Long> {

    List<TestGenerationIteration> findAllByJobIdOrderByIterationNoAsc(Long jobId);
}
