package ua.kpi.grader.submission.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import ua.kpi.grader.submission.feedback.TestCaseStatus;

import java.time.OffsetDateTime;

/** Result of one test case of an attempt, parsed from the CI job log. */
@Entity
@Table(name = "attempt_test_results")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttemptTestResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attempt_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_attempt_test_results_attempts"))
    private Attempt attempt;

    @Column(nullable = false)
    private Integer position;

    @Column(nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TestCaseStatus status;

    @Column(columnDefinition = "TEXT")
    private String expected;

    @Column(columnDefinition = "TEXT")
    private String actual;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(name = "duration_ms")
    private Long durationMs;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
