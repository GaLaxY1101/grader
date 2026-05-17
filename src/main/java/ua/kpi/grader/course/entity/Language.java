package ua.kpi.grader.course.entity;

public enum Language {

    C("C", "solution.c"),
    CPP("C++", "solution.cpp");

    private final String displayName;
    private final String solutionFileName;

    Language(String displayName, String solutionFileName) {
        this.displayName = displayName;
        this.solutionFileName = solutionFileName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getSolutionFileName() {
        return solutionFileName;
    }
}
