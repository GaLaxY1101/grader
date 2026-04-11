package ua.kpi.grader.course.entity;

import jakarta.persistence.*;
import lombok.*;

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

    @Column(nullable = false, length = 50)
    private String language;

    @Column(name = "gitlab_project_template", length = 500)
    private String gitlabProjectTemplate;

    @Column(name = "ci_config_template", columnDefinition = "TEXT")
    private String ciConfigTemplate;
}
