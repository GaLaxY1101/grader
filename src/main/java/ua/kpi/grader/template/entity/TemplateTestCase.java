package ua.kpi.grader.template.entity;

import jakarta.persistence.*;
import lombok.*;
import ua.kpi.grader.course.entity.TestType;

@Entity
@Table(name = "template_test_cases")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateTestCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_programming_task_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_template_test_cases_template_programming_tasks"))
    private TemplateProgrammingTask programmingTask;

    @Column(nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "test_type", nullable = false, length = 20)
    private TestType testType;

    @Column(columnDefinition = "TEXT")
    private String input;

    @Column(name = "expected_output", columnDefinition = "TEXT")
    private String expectedOutput;
}
