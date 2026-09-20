package ua.kpi.grader.user.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ua.kpi.grader.user.entity.User;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /**
     * Searches users by a case-insensitive term matched against email, first name
     * or last name. Null or blank {@code query} returns every user.
     *
     * @param query    normalized search term (already trimmed &amp; lower-cased,
     *                 or {@code null} to disable text filtering)
     * @param pageable paging &amp; sort
     */
    @Query("""
            SELECT u FROM User u
            WHERE CAST(:query AS string) IS NULL
               OR LOWER(u.email)     LIKE CONCAT('%', CAST(:query AS string), '%')
               OR LOWER(u.firstName) LIKE CONCAT('%', CAST(:query AS string), '%')
               OR LOWER(u.lastName)  LIKE CONCAT('%', CAST(:query AS string), '%')
            """)
    Page<User> search(@Param("query") String query, Pageable pageable);

    @Query("SELECT u.email FROM User u")
    List<String> findAllEmails();
}
