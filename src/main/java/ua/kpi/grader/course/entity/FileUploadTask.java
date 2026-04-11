package ua.kpi.grader.course.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

@Entity
@Table(name = "file_upload_tasks")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_file_upload_tasks_assignments"))
    private Assignment assignment;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "allowed_extensions", columnDefinition = "TEXT[]")
    private List<String> allowedExtensions;

    @Column(name = "max_file_size")
    private Integer maxFileSize;

    @Column(name = "allowed_file_count", nullable = false)
    @Builder.Default
    private Integer allowedFileCount = 1;
}
