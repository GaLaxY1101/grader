package ua.kpi.grader.group.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import ua.kpi.grader.user.entity.Student;

import java.time.OffsetDateTime;

@Entity
@Table(name = "group_students")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupStudent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_group_students_academic_groups"))
    private AcademicGroup group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_group_students_students"))
    private Student student;

    @CreationTimestamp
    @Column(name = "enrolled_at", nullable = false, updatable = false)
    private OffsetDateTime enrolledAt;

    @Column(name = "graduated_at")
    private OffsetDateTime graduatedAt;
}
