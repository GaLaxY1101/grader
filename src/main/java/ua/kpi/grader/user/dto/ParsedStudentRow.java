package ua.kpi.grader.user.dto;

import java.util.List;

/**
 * A single row returned by the XLSX parse endpoint. Sent to the frontend so
 * the teacher can review, fix, and edit rows in an inline table before
 * committing.
 *
 * <p>{@code phone} is E.164-normalised when parsing succeeded, or the raw
 * teacher-entered value when parsing failed — in the latter case the failure
 * is recorded in {@code errors} and the frontend can show the raw value for
 * inline correction.
 *
 * <p>{@code errors} is empty for valid rows.
 */
public record ParsedStudentRow(
        int rowNumber,
        String email,
        String firstName,
        String lastName,
        String phone,
        List<String> errors
) {}
