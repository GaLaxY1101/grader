package ua.kpi.grader.course.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import ua.kpi.grader.user.entity.Teacher;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "assignments")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Assignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_assignments_courses"))
    private Course course;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "max_score", nullable = false)
    @Builder.Default
    private Integer maxScore = 100;

    @Column(name = "deadline")
    private OffsetDateTime deadline;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @Column(name = "code_check_enabled", nullable = false)
    @Builder.Default
    private boolean codeCheckEnabled = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false,
            foreignKey = @ForeignKey(name = "fk_assignments_teachers"))
    private Teacher createdBy;

    @Setter
    @OneToOne(mappedBy = "assignment", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private ProgrammingTask programmingTask;

    @OneToMany(mappedBy = "assignment", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<AssignmentAttachment> attachments = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /**
     * Updates mutable fields on this assignment.
     */
    public void update(String title, String description, Integer maxScore, OffsetDateTime deadline) {
        this.title = title;
        this.maxScore = maxScore;
        if (description != null) this.description = description;
        if (deadline != null) this.deadline = deadline;
    }

    /**
     * Soft-deletes this assignment by marking it inactive.
     */
    public void deactivate() {
        this.isActive = false;
    }

    /**
     * Adds a teacher-uploaded attachment while maintaining the bidirectional association.
     */
    public void addAttachment(AssignmentAttachment attachment) {
        this.attachments.add(attachment);
    }

    /**
     * Removes an attachment, breaking the association so orphanRemoval deletes it.
     */
    public void removeAttachment(AssignmentAttachment attachment) {
        this.attachments.remove(attachment);
    }
}
