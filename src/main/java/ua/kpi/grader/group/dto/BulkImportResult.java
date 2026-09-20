package ua.kpi.grader.group.dto;

import java.util.List;

/**
 * Result of a successful bulk-import commit.
 *
 * @param groupId    the target group (either newly created or pre-existing)
 * @param totalRows  the number of student rows submitted
 * @param created    rows that resulted in a brand-new {@code User} + {@code Student}
 * @param linked     rows whose email already matched an existing {@code User};
 *                   the user was reused and only a new {@code GroupStudent}
 *                   membership was created
 */
public record BulkImportResult(
        Long groupId,
        int totalRows,
        List<ImportedStudent> created,
        List<ImportedStudent> linked
) {

    public record ImportedStudent(
            Long userId,
            Long studentId,
            String email
    ) {}
}
