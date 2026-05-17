package ua.kpi.grader.course.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "programming_tasks")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProgrammingTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_programming_tasks_assignments"))
    private Assignment assignment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Language language;

    @Column(name = "ci_config_template", columnDefinition = "TEXT")
    private String ciConfigTemplate;

    @Enumerated(EnumType.STRING)
    @Column(name = "test_mode", nullable = false, length = 20)
    @Builder.Default
    private TestMode testMode = TestMode.IO;

    @Column(name = "function_signature", columnDefinition = "TEXT")
    private String functionSignature;

    @Column(name = "test_file_content", columnDefinition = "TEXT")
    private String testFileContent;

    @OneToMany(mappedBy = "programmingTask", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<TestCase> testCases = new ArrayList<>();

    /**
     * Updates mutable fields of the programming task.
     */
    public void update(TestMode testMode, String functionSignature,
                       String testFileContent, String ciConfigTemplate) {
        this.testMode = testMode;
        this.functionSignature = functionSignature;
        this.testFileContent = testFileContent;
        this.ciConfigTemplate = ciConfigTemplate;
    }

    /**
     * Replaces all test cases with the provided list.
     */
    public void replaceTestCases(List<TestCase> newTestCases) {
        this.testCases.clear();
        this.testCases.addAll(newTestCases);
    }
}
