package ua.kpi.grader.course.entity;

import jakarta.persistence.*;
import lombok.*;
import ua.kpi.grader.user.entity.Teacher;

@Entity
@Table(name = "course_teachers")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseTeacher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_course_teachers_courses"))
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_course_teachers_teachers"))
    private Teacher teacher;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String role = "LECTURER";
}
