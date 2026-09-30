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

    @Column
    private Integer grade;

    @Column(name = "gitlab_project_id")
    private Long gitlabProjectId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "latest_attempt_id",
            foreignKey = @ForeignKey(name = "fk_submissions_latest_attempt"))
    private Attempt latestAttempt;

    @OneToMany(mappedBy = "submission", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Attempt> attempts = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "file_state", length = 20)
    private SubmissionFileState fileState;

    @Column(name = "return_comment", columnDefinition = "TEXT")
    private String returnComment;

    @Column(name = "returned_at")
    private OffsetDateTime returnedAt;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @OneToMany(mappedBy = "submission", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SubmissionAttachment> attachments = new ArrayList<>();

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
     * Auto-populates grade on first PASSED result; never overwrites an
     * existing grade (teacher overrides survive future auto-runs).
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

        if (this.grade == null
                && attempt.getStatus() == SubmissionStatus.PASSED
                && this.bestScore != null) {
            this.grade = this.bestScore;
        }
    }

    /**
     * Sets the teacher-assigned grade. Pass null to clear.
     */
    public void assignGrade(Integer newGrade) {
        this.grade = newGrade;
    }

    /**
     * Initialises the file workflow state on first student upload for a file-bearing assignment.
     * No-op if the state is already set.
     */
    public void initFileStateIfNeeded() {
        if (this.fileState == null) {
            this.fileState = SubmissionFileState.DRAFT;
        }
    }

    /**
     * Transitions the file workflow from DRAFT or RETURNED into SUBMITTED and records the timestamp.
     *
     * @throws IllegalStateException if the current state does not permit turn-in
     */
    public void turnIn() {
        if (this.fileState != SubmissionFileState.DRAFT && this.fileState != SubmissionFileState.RETURNED) {
            throw new IllegalStateException("Cannot turn in submission in state " + this.fileState);
        }
        this.fileState = SubmissionFileState.SUBMITTED;
        this.submittedAt = OffsetDateTime.now();
    }

    /**
     * Teacher returns the submission for redo. Preserves any tentative grade (Google Classroom parity).
     *
     * @throws IllegalStateException if the submission is not currently SUBMITTED
     */
    public void markReturned(String comment) {
        if (this.fileState != SubmissionFileState.SUBMITTED) {
            throw new IllegalStateException("Cannot return submission in state " + this.fileState);
        }
        this.fileState = SubmissionFileState.RETURNED;
        this.returnComment = comment;
        this.returnedAt = OffsetDateTime.now();
    }

    /**
     * Flips file workflow to GRADED after a teacher grade assignment, if a file workflow is in progress.
     */
    public void markGradedIfFileWorkflow() {
        if (this.fileState == SubmissionFileState.SUBMITTED || this.fileState == SubmissionFileState.RETURNED) {
            this.fileState = SubmissionFileState.GRADED;
        }
    }

    /**
     * Adds a new attachment while maintaining the bidirectional association.
     */
    public void addAttachment(SubmissionAttachment attachment) {
        this.attachments.add(attachment);
    }

    /**
     * Removes an attachment, breaking the association so orphanRemoval deletes it.
     */
    public void removeAttachment(SubmissionAttachment attachment) {
        this.attachments.remove(attachment);
    }
}
