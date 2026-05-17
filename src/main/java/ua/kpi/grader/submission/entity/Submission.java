package ua.kpi.grader.submission.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import ua.kpi.grader.course.entity.Assignment;
import ua.kpi.grader.user.entity.Student;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "submissions",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_submissions_assignment_student",
                columnNames = {"assignment_id", "student_id"}))
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

    @Column
    private Integer score;

    @Column(name = "best_score")
    private Integer bestScore;

    @Column(name = "gitlab_project_id")
    private Long gitlabProjectId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "latest_attempt_id",
            foreignKey = @ForeignKey(name = "fk_submissions_latest_attempt"))
    private Attempt latestAttempt;

    @OneToMany(mappedBy = "submission", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Attempt> attempts = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /**
     * Records the GitLab project ID after the first attempt creates the project.
     */
    public void assignGitlabProject(Long projectId) {
        this.gitlabProjectId = projectId;
    }

    /**
     * Propagates attempt results to the submission aggregate.
     * Always updates bestScore. Only updates status/score/latestAttempt
     * if this attempt is the most recent one (highest attempt number).
     */
    public void updateFromAttempt(Attempt attempt) {
        if (attempt.getScore() != null) {
            this.bestScore = (this.bestScore == null)
                    ? attempt.getScore()
                    : Math.max(this.bestScore, attempt.getScore());
        }

        if (this.latestAttempt == null
                || attempt.getAttemptNumber() >= this.latestAttempt.getAttemptNumber()) {
            this.status = attempt.getStatus();
            this.score = attempt.getScore();
            this.latestAttempt = attempt;
        }
    }
}
