package ua.kpi.grader.course.entity;

/**
 * Determines how a programming task is tested.
 */
public enum TestMode {

    /** Traditional input/output test cases with diff comparison. */
    IO,

    /** Unit test mode: teacher provides test.cpp, student implements a function. */
    UNIT_TEST
}
