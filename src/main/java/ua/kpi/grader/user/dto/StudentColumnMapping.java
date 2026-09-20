package ua.kpi.grader.user.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Teacher-supplied mapping from the logical row fields to the actual header
 * strings present in the uploaded XLSX file.
 *
 * <p>Header matching is case-insensitive and whitespace-trimmed.
 * The {@code phone} field is optional: when {@code null} or blank, the parser
 * ignores any phone column in the file and every row's phone is left {@code null}.
 */
public record StudentColumnMapping(

        @NotBlank
        String email,

        @NotBlank
        String firstName,

        @NotBlank
        String lastName,

        String phone
) {}
