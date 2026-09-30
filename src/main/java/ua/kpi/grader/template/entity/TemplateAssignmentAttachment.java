package ua.kpi.grader.template.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import ua.kpi.grader.user.entity.Teacher;

import java.time.OffsetDateTime;

@Entity
@Table(name = "template_assignment_attachments")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateAssignmentAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_assignment_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_template_assignment_attachments_template_assignments"))
    private TemplateAssignment templateAssignment;

    @Column(nullable = false, length = 512)
    private String filename;

    @Column(name = "content_type", length = 255)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(name = "storage_key", nullable = false, length = 1024, unique = true)
    private String storageKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by", nullable = false,
            foreignKey = @ForeignKey(name = "fk_template_assignment_attachments_teachers"))
    private Teacher uploadedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
