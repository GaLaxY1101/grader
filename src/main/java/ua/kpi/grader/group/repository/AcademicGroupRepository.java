package ua.kpi.grader.group.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ua.kpi.grader.group.entity.AcademicGroup;

import java.util.List;

public interface AcademicGroupRepository extends JpaRepository<AcademicGroup, Long> {

    List<AcademicGroup> findAllByIsActiveTrue();

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);
}
