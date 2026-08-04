package ua.kpi.grader.template.entity;

import jakarta.persistence.*;
import lombok.*;
import ua.kpi.grader.course.entity.Language;
import ua.kpi.grader.course.entity.TestMode;

@Entity
@Table(name = "template_programming_tasks")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateProgrammingTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_assignment_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_template_programming_tasks_template_assignments"))
    private TemplateAssignment assignment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Language language;

    @Column(name = "ci_config_template", columnDefinition = "TEXT")
    private String ciConfigTemplate;

    @Enumerated(EnumType.STRING)
    @Column(name = "test_mode", nullable = false, length = 20)
    @Builder.Default
    private TestMode testMode = TestMode.UNIT_TEST;

    @Column(name = "function_signature", columnDefinition = "TEXT")
    private String functionSignature;

    @Column(name = "test_file_content", columnDefinition = "TEXT")
    private String testFileContent;

    /**
     * Updates mutable fields of the template programming task.
     */
    public void update(TestMode testMode, String functionSignature,
                       String testFileContent, String ciConfigTemplate) {
        this.testMode = testMode;
        this.functionSignature = functionSignature;
        this.testFileContent = testFileContent;
        this.ciConfigTemplate = ciConfigTemplate;
    }
}
