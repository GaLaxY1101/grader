package ua.kpi.grader.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A single student row as sent by the frontend at bulk-import commit time.
 * The frontend has already normalised the value via the parse endpoint,
 * but the commit service re-normalises to guard against teacher-edited values
 * that never went through the parser.
 */
public record StudentInput(

        @NotBlank @Email @Size(max = 255)
        String email,

        @NotBlank @Size(max = 100)
        String firstName,

        @NotBlank @Size(max = 100)
        String lastName,

        @Size(max = 30)
        String phone
) {}
