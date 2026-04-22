package ua.kpi.grader.submission.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import ua.kpi.grader.course.entity.Assignment;
import ua.kpi.grader.user.entity.Student;

import java.time.OffsetDateTime;

@Entity
@Table(name = "submissions")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Submission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_submissions_assignments"))
    private Assignment assignment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_submissions_students"))
    private Student student;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SubmissionStatus status = SubmissionStatus.PENDING;

    @Column(name = "code_content", columnDefinition = "TEXT")
    private String codeContent;

    @Column
    private Integer score;

    @Column(name = "gitlab_pipeline_id")
    private Long gitlabPipelineId;

    @Column(name = "pipeline_output", columnDefinition = "TEXT")
    private String pipelineOutput;

    @Column(name = "submitted_at", nullable = false)
    @Builder.Default
    private OffsetDateTime submittedAt = OffsetDateTime.now();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /**
     * Marks the submission as RUNNING and records the GitLab pipeline ID.
     */
    public void startPipeline(Long pipelineId) {
        this.gitlabPipelineId = pipelineId;
        this.status = SubmissionStatus.RUNNING;
    }

    /**
     * Applies the pipeline result: status, score, and raw output.
     */
    public void applyResult(SubmissionStatus newStatus, Integer newScore, String output) {
        this.status = newStatus;
        this.score = newScore;
        this.pipelineOutput = output;
    }
}
