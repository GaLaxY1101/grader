package ua.kpi.grader.common.exception;

import java.util.List;

/**
 * Thrown by the bulk-import commit service when pre-validation of the submitted
 * student list finds one or more row-level problems (bean-validation failures,
 * duplicate emails within the batch, students already active in another group, etc.).
 *
 * <p>The batch is rejected atomically — no database writes happen.
 * The carried {@link RowError} list lets the client highlight the offending
 * rows in its editable table.
 */
public class InvalidImportException extends RuntimeException {

    private final List<RowError> errors;

    public InvalidImportException(List<RowError> errors) {
        super("Bulk import rejected: " + errors.size() + " row error(s)");
        this.errors = List.copyOf(errors);
    }

    public List<RowError> getErrors() {
        return errors;
    }

    /**
     * A single row-level validation problem.
     *
     * @param rowNumber 1-based index of the row within the submitted batch
     * @param field     the field the message applies to, or {@code null} for row-wide errors
     * @param message   human-readable explanation the client can display inline
     */
    public record RowError(int rowNumber, String field, String message) {}
}
