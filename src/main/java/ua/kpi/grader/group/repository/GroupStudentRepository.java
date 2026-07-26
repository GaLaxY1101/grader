package ua.kpi.grader.group.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ua.kpi.grader.group.entity.GroupStudent;

import java.util.List;
import java.util.Optional;

public interface GroupStudentRepository extends JpaRepository<GroupStudent, Long> {

    @Query("SELECT gs FROM GroupStudent gs JOIN FETCH gs.student s JOIN FETCH s.user WHERE gs.group.id = :groupId")
    List<GroupStudent> findAllByGroupIdWithStudentUser(@Param("groupId") Long groupId);

    boolean existsByGroupIdAndStudentId(Long groupId, Long studentId);

    Optional<GroupStudent> findByGroupIdAndStudentId(Long groupId, Long studentId);

    @Query("SELECT gs FROM GroupStudent gs JOIN FETCH gs.group WHERE gs.graduatedAt IS NULL")
    List<GroupStudent> findAllActiveWithGroup();

    @Query("SELECT gs FROM GroupStudent gs JOIN FETCH gs.group WHERE gs.student.id = :studentId AND gs.graduatedAt IS NULL")
    Optional<GroupStudent> findActiveByStudentId(@Param("studentId") Long studentId);
}
