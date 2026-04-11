package ua.kpi.grader.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ua.kpi.grader.user.entity.Teacher;

import java.util.Optional;

public interface TeacherRepository extends JpaRepository<Teacher, Long> {

    Optional<Teacher> findByUserId(Long userId);

    boolean existsByUserId(Long userId);
}
