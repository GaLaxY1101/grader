package ua.kpi.grader.common.exception;

import java.util.List;

/**
 * Thrown by the bulk-import XLSX reader when a required header column
 * (per the teacher-supplied column mapping) is absent from the uploaded file.
 *
 * <p>Carries both the missing header and the list of headers actually detected
 * in the file, so the client can help the teacher fix the mapping without
 * re-opening Excel.
 */
public class MissingColumnException extends RuntimeException {

    private final String missingHeader;
    private final List<String> detectedHeaders;

    public MissingColumnException(String missingHeader, List<String> detectedHeaders) {
        super("Column '" + missingHeader + "' not found in uploaded file. Detected headers: " + detectedHeaders);
        this.missingHeader = missingHeader;
        this.detectedHeaders = List.copyOf(detectedHeaders);
    }

    public String getMissingHeader() {
        return missingHeader;
    }

    public List<String> getDetectedHeaders() {
        return detectedHeaders;
    }
}
