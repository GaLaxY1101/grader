package ua.kpi.grader.submission.entity;

public enum SubmissionFileState {

    /** Student is still uploading. Files editable. */
    DRAFT,

    /** Student has turned in. Files locked; awaiting teacher action. */
    SUBMITTED,

    /** Teacher returned for redo. Student may edit files and turn in again. Grade preserved. */
    RETURNED,

    /** Teacher assigned a final grade. Terminal for the file workflow. */
    GRADED;

    public boolean isEditable() {
        return this == DRAFT || this == RETURNED;
    }

    /**
     * States that allow a student to attach additional files.
     * GRADED is included so students can supplement a graded submission; the grade is preserved.
     */
    public boolean isUploadAllowed() {
        return this == DRAFT || this == RETURNED || this == GRADED;
    }
}
