package ua.kpi.grader.user.dto;

import java.util.List;

/**
 * Response from the bulk-import parse endpoint.
 *
 * @param rows            one entry per non-blank data row in the file
 * @param detectedHeaders header strings actually present in the file's first row —
 *                        surfaced so the frontend can help the teacher fix the
 *                        column mapping if a required column was missing
 */
public record ParsedStudentsResponse(
        List<ParsedStudentRow> rows,
        List<String> detectedHeaders
) {}
