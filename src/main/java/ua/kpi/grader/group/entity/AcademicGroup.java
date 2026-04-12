package ua.kpi.grader.group.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "academic_groups")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AcademicGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 100)
    private String faculty;

    @Column(length = 100)
    private String speciality;

    @Column(name = "year_of_creation", nullable = false)
    private Integer yearOfCreation;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /**
     * Updates mutable fields on this group.
     */
    public void update(String code, String name, String faculty,
                       String speciality, Integer yearOfCreation) {
        this.code = code;
        this.name = name;
        this.faculty = faculty;
        this.speciality = speciality;
        this.yearOfCreation = yearOfCreation;
    }

    /**
     * Soft-deletes this group by marking it inactive.
     */
    public void deactivate() {
        this.isActive = false;
    }
}
