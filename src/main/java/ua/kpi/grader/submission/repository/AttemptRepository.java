package ua.kpi.grader.submission.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ua.kpi.grader.submission.entity.Attempt;

import java.util.List;
import java.util.Optional;

public interface AttemptRepository extends JpaRepository<Attempt, Long> {

    Optional<Attempt> findByGitlabPipelineId(Long gitlabPipelineId);

    @Query("SELECT COALESCE(MAX(a.attemptNumber), 0) FROM Attempt a WHERE a.submission.id = :submissionId")
    int findMaxAttemptNumber(@Param("submissionId") Long submissionId);

    @Query("""
            SELECT a FROM Attempt a
            JOIN FETCH a.submission s
            JOIN FETCH s.student st
            JOIN FETCH st.user
            JOIN FETCH s.assignment
            WHERE a.submission.id = :submissionId
            ORDER BY a.attemptNumber DESC
            """)
    List<Attempt> findAllBySubmissionIdOrderByAttemptNumberDesc(@Param("submissionId") Long submissionId);

    @Query("""
            SELECT a FROM Attempt a
            JOIN FETCH a.submission s
            JOIN FETCH s.student st
            JOIN FETCH st.user
            JOIN FETCH s.assignment
            WHERE a.id = :id
            """)
    Optional<Attempt> findByIdWithDetails(@Param("id") Long id);
}
