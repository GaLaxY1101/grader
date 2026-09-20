package ua.kpi.grader.group.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ua.kpi.grader.group.entity.GroupStudent;

import java.util.List;
import java.util.Optional;

public interface GroupStudentRepository extends JpaRepository<GroupStudent, Long> {

    @Query("SELECT gs FROM GroupStudent gs JOIN FETCH gs.student s JOIN FETCH s.user WHERE gs.group.id = :groupId")
    List<GroupStudent> findAllByGroupIdWithStudentUser(@Param("groupId") Long groupId);

    /**
     * Paginated search of a group's memberships. Term matches email, first name
     * or last name of the underlying user (case-insensitive). Null or blank
     * query returns every member of the group.
     */
    @Query("""
            SELECT gs FROM GroupStudent gs
              JOIN gs.student s
              JOIN s.user u
            WHERE gs.group.id = :groupId
              AND (CAST(:query AS string) IS NULL
                   OR LOWER(u.email)     LIKE CONCAT('%', CAST(:query AS string), '%')
                   OR LOWER(u.firstName) LIKE CONCAT('%', CAST(:query AS string), '%')
                   OR LOWER(u.lastName)  LIKE CONCAT('%', CAST(:query AS string), '%'))
            """)
    Page<GroupStudent> searchByGroupId(@Param("groupId") Long groupId,
                                       @Param("query") String query,
                                       Pageable pageable);

    boolean existsByGroupIdAndStudentId(Long groupId, Long studentId);

    Optional<GroupStudent> findByGroupIdAndStudentId(Long groupId, Long studentId);

    @Query("SELECT gs FROM GroupStudent gs JOIN FETCH gs.group WHERE gs.graduatedAt IS NULL")
    List<GroupStudent> findAllActiveWithGroup();

    @Query("SELECT gs FROM GroupStudent gs JOIN FETCH gs.group WHERE gs.student.id = :studentId AND gs.graduatedAt IS NULL")
    Optional<GroupStudent> findActiveByStudentId(@Param("studentId") Long studentId);
}
