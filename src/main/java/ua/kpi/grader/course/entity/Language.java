package ua.kpi.grader.course.entity;

public enum Language {

    C("C", "solution.c", "test.cpp"),
    CPP("C++", "solution.cpp", "test.cpp"),
    PYTHON("Python", "solution.py", "test_solution.py");

    private final String displayName;
    private final String solutionFileName;
    private final String testFileName;

    Language(String displayName, String solutionFileName, String testFileName) {
        this.displayName = displayName;
        this.solutionFileName = solutionFileName;
        this.testFileName = testFileName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getSolutionFileName() {
        return solutionFileName;
    }

    public String getTestFileName() {
        return testFileName;
    }
}
