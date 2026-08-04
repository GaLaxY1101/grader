package ua.kpi.grader.template.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import ua.kpi.grader.user.entity.Teacher;

import java.time.OffsetDateTime;

@Entity
@Table(name = "template_shares",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_template_shares_template_teacher",
                columnNames = {"template_id", "shared_with_teacher_id"}))
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateShare {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_template_shares_course_templates"))
    private CourseTemplate template;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shared_with_teacher_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_template_shares_teachers_shared_with"))
    private Teacher sharedWithTeacher;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shared_by_teacher_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_template_shares_teachers_shared_by"))
    private Teacher sharedByTeacher;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
