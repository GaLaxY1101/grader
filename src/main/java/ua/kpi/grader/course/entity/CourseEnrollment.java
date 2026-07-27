package ua.kpi.grader.course.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import ua.kpi.grader.user.entity.Student;

import java.time.OffsetDateTime;

@Entity
@Table(name = "course_enrollments")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseEnrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_course_enrollments_courses"))
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_course_enrollments_students"))
    private Student student;

    @CreationTimestamp
    @Column(name = "enrolled_at", nullable = false, updatable = false)
    private OffsetDateTime enrolledAt;

    @Column(name = "final_grade")
    private Integer finalGrade;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    /**
     * Marks this enrollment as dropped.
     */
    public void drop() {
        this.status = "DROPPED";
    }

    /**
     * Reactivates a previously dropped enrollment.
     */
    public void reactivate() {
        this.status = "ACTIVE";
    }
}
