package ua.kpi.grader.submission.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ua.kpi.grader.submission.entity.Submission;

import java.util.List;
import java.util.Optional;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    @Query("""
            SELECT s FROM Submission s
            JOIN FETCH s.student st
            JOIN FETCH st.user
            JOIN FETCH s.assignment
            WHERE s.id = :id
            """)
    Optional<Submission> findByIdWithDetails(@Param("id") Long id);

    @Query("""
            SELECT s FROM Submission s
            JOIN FETCH s.student st
            JOIN FETCH st.user
            JOIN FETCH s.assignment
            WHERE s.assignment.id = :assignmentId
            ORDER BY s.updatedAt DESC
            """)
    List<Submission> findAllByAssignmentIdOrderByUpdatedAtDesc(@Param("assignmentId") Long assignmentId);

    @Query("""
            SELECT s FROM Submission s
            JOIN FETCH s.student st
            JOIN FETCH st.user
            JOIN FETCH s.assignment
            WHERE s.assignment.id = :assignmentId AND s.student.id = :studentId
            """)
    Optional<Submission> findByAssignmentIdAndStudentId(
            @Param("assignmentId") Long assignmentId,
            @Param("studentId") Long studentId);
}
