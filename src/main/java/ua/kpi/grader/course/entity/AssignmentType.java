package ua.kpi.grader.course.entity;

public enum AssignmentType {

    /** Code-only: submits {@code codeContent}, runs GitLab CI, uses Attempts. */
    CODE,

    /** Files-only: no CI, uses {@code file_state} workflow, no Attempts created. */
    FILE,

    /** Hybrid: code path (Attempts + GitLab) plus student file attachments reviewed manually. */
    CODE_FILE;

    public boolean supportsCode() {
        return this == CODE || this == CODE_FILE;
    }

    public boolean supportsFiles() {
        return this == FILE || this == CODE_FILE;
    }
}
