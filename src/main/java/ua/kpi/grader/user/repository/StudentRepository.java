package ua.kpi.grader.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ua.kpi.grader.user.entity.Student;

import java.util.Optional;

public interface StudentRepository extends JpaRepository<Student, Long> {

    Optional<Student> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    Optional<Student> findByUser_Email(String email);
}
