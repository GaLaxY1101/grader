package ua.kpi.grader.course.entity;

public enum TestType {
    /** Input → expected output comparison via diff. */
    IO,
    /** Program must exit with a non-zero exit code. */
    EXCEPTION
}
