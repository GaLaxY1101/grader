package ua.kpi.grader.submission.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import ua.kpi.grader.submission.feedback.TestCaseResult;
import ua.kpi.grader.submission.feedback.TestReport;
import ua.kpi.grader.submission.feedback.TestRunStatus;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "attempts")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Attempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_attempts_submissions"))
    private Submission submission;

    @Column(name = "attempt_number", nullable = false)
    private Integer attemptNumber;

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

    @Column(name = "tests_passed")
    private Integer testsPassed;

    @Column(name = "tests_total")
    private Integer testsTotal;

    /** Overall outcome parsed from the log; null for attempts graded before structured feedback existed. */
    @Enumerated(EnumType.STRING)
    @Column(name = "test_run_status", length = 20)
    private TestRunStatus testRunStatus;

    @Column(name = "compile_output", columnDefinition = "TEXT")
    private String compileOutput;

    @OneToMany(mappedBy = "attempt", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    @Builder.Default
    private List<AttemptTestResult> testResults = new ArrayList<>();

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
     * Marks the attempt as RUNNING and records the GitLab pipeline ID.
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

    /**
     * Replaces the structured test results with the given parsed report.
     * A report without details clears the counters, so only the raw log is shown.
     */
    public void applyTestReport(TestReport report) {
        this.testResults.clear();
        if (!report.detailsAvailable()) {
            this.testsPassed = null;
            this.testsTotal = null;
            this.testRunStatus = null;
            this.compileOutput = null;
            return;
        }
        this.testsPassed = report.passed();
        this.testsTotal = report.total();
        this.testRunStatus = report.status();
        this.compileOutput = report.compileOutput();
        int position = 0;
        for (TestCaseResult test : report.tests()) {
            AttemptTestResult result = AttemptTestResult.builder()
                    .position(position++)
                    .name(test.name())
                    .status(test.status())
                    .expected(test.expected())
                    .actual(test.actual())
                    .message(test.message())
                    .durationMs(test.durationMs())
                    .build();
            result.setAttempt(this);
            this.testResults.add(result);
        }
    }
}
