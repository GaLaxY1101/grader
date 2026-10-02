package ua.kpi.grader.testgen.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import ua.kpi.grader.course.entity.Assignment;
import ua.kpi.grader.course.entity.Language;

import java.time.OffsetDateTime;

/**
 * One AI test generation run. Iterations are stored separately and loaded through
 * {@code TestGenerationIterationRepository} (no collection mapping, so saving a detached
 * job from the background worker can never orphan-delete iterations).
 */
@Entity
@Table(name = "test_generation_jobs")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestGenerationJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Assignment the tests are generated for; null for unsaved assignment drafts. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id",
            foreignKey = @ForeignKey(name = "fk_test_generation_jobs_assignments"))
    private Assignment assignment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private JobStatus status = JobStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Language language;

    @Column(nullable = false, length = 100)
    private String model;

    /** Effective generation config (iterations, temperature, seed, ...) serialized as JSON. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String config;

    @Column(name = "task_description", columnDefinition = "TEXT")
    private String taskDescription;

    @Column(name = "function_signature", columnDefinition = "TEXT")
    private String functionSignature;

    @Column(name = "reference_solution", nullable = false, columnDefinition = "TEXT")
    private String referenceSolution;

    @Column(name = "final_test_content", columnDefinition = "TEXT")
    private String finalTestContent;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /** Keycloak user id (JWT subject) of the teacher who started the job. */
    @Column(name = "created_by", length = 255)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    /**
     * Marks the job as running.
     */
    public void markRunning() {
        this.status = JobStatus.RUNNING;
    }

    /**
     * Marks the job as succeeded with the final, validated test file.
     */
    public void markSucceeded(String finalTestContent) {
        this.status = JobStatus.SUCCEEDED;
        this.finalTestContent = finalTestContent;
        this.finishedAt = OffsetDateTime.now();
    }

    /**
     * Marks the job as failed with a human-readable reason.
     */
    public void markFailed(String errorMessage) {
        this.status = JobStatus.FAILED;
        this.errorMessage = errorMessage;
        this.finishedAt = OffsetDateTime.now();
    }
}
