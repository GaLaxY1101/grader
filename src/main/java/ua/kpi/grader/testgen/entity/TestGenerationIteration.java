package ua.kpi.grader.testgen.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

/**
 * One LLM call of the self-repair loop together with the sandbox metrics of the
 * resulting test file. Stored in full (prompt and raw output included) as evaluation data.
 */
@Entity
@Table(name = "test_generation_iterations")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestGenerationIteration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_test_generation_iterations_test_generation_jobs"))
    private TestGenerationJob job;

    @Column(name = "iteration_no", nullable = false)
    private Integer iterationNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "prompt_type", nullable = false, length = 30)
    private PromptType promptType;

    @Column(columnDefinition = "TEXT")
    private String prompt;

    @Column(name = "llm_output", columnDefinition = "TEXT")
    private String llmOutput;

    @Column(name = "test_content", columnDefinition = "TEXT")
    private String testContent;

    @Column(name = "compile_ok")
    private Boolean compileOk;

    @Column(name = "ref_passed")
    private Integer refPassed;

    @Column(name = "ref_total")
    private Integer refTotal;

    @Column(name = "mutants_killed")
    private Integer mutantsKilled;

    @Column(name = "mutants_total")
    private Integer mutantsTotal;

    @Column(name = "test_count")
    private Integer testCount;

    private Boolean accepted;

    @Column(columnDefinition = "TEXT")
    private String feedback;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
