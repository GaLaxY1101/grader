package ua.kpi.grader.template.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import ua.kpi.grader.course.entity.AssignmentType;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "template_assignments")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_template_assignments_course_templates"))
    private CourseTemplate template;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "max_score", nullable = false)
    @Builder.Default
    private Integer maxScore = 100;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AssignmentType type = AssignmentType.CODE;

    @Setter
    @OneToOne(mappedBy = "assignment", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private TemplateProgrammingTask programmingTask;

    @OneToMany(mappedBy = "templateAssignment", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<TemplateAssignmentAttachment> attachments = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /**
     * Updates mutable fields on this template assignment.
     */
    public void update(String title, String description, Integer maxScore) {
        this.title = title;
        this.maxScore = maxScore;
        if (description != null) this.description = description;
    }

    /**
     * Adds an attachment while maintaining the bidirectional association.
     */
    public void addAttachment(TemplateAssignmentAttachment attachment) {
        this.attachments.add(attachment);
    }

    /**
     * Removes an attachment, breaking the association so orphanRemoval deletes it.
     */
    public void removeAttachment(TemplateAssignmentAttachment attachment) {
        this.attachments.remove(attachment);
    }
}
