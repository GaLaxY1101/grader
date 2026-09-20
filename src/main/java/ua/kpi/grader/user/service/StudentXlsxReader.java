package ua.kpi.grader.user.service;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import ua.kpi.grader.common.exception.InvalidPhoneNumberException;
import ua.kpi.grader.common.exception.MissingColumnException;
import ua.kpi.grader.common.util.PhoneNumberNormalizer;
import ua.kpi.grader.user.dto.ParsedStudentRow;
import ua.kpi.grader.user.dto.StudentColumnMapping;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads a bulk-import XLSX file into {@link ParsedStudentRow} instances.
 *
 * <p>Header lookup is case-insensitive and whitespace-trimmed. Row-level
 * problems (missing required cell, unparseable phone) are recorded on the
 * returned rows rather than thrown, so the frontend can display every issue
 * at once and let the teacher fix them inline.
 *
 * <p>The only fatal condition is a missing required column in the header row
 * itself — reported via {@link MissingColumnException}.
 */
@Component
@RequiredArgsConstructor
public class StudentXlsxReader {

    private static final DataFormatter DATA_FORMATTER = new DataFormatter();

    private final PhoneNumberNormalizer phoneNumberNormalizer;

    /**
     * Reads only the header row (row 0) and returns the header strings in the
     * order they appear. Empty header cells are skipped.
     *
     * @param in the XLSX byte stream
     * @return the detected headers (may be empty)
     */
    public List<String> readHeaders(InputStream in) {
        try (Workbook wb = new XSSFWorkbook(in)) {
            Sheet sheet = wb.getSheetAt(0);
            return collectHeaders(sheet);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read XLSX file", e);
        }
    }

    /**
     * Parses every non-blank data row against the caller-supplied column mapping.
     *
     * @param in      the XLSX byte stream
     * @param mapping which header string identifies each logical field;
     *                {@code mapping.phone()} may be {@code null}/blank to skip
     *                the phone column entirely
     * @return one row per non-blank data row in the file; row-level problems
     *         (missing required cell, invalid phone) are recorded on the row's
     *         {@code errors} list, not thrown
     * @throws MissingColumnException if any required header from the mapping
     *                                is absent from the file
     */
    public List<ParsedStudentRow> read(InputStream in, StudentColumnMapping mapping) {
        try (Workbook wb = new XSSFWorkbook(in)) {
            Sheet sheet = wb.getSheetAt(0);
            List<String> detectedHeaders = collectHeaders(sheet);
            Map<String, Integer> indexByHeader = buildHeaderIndex(sheet);

            int emailCol = requireColumn(mapping.email(), indexByHeader, detectedHeaders);
            int firstNameCol = requireColumn(mapping.firstName(), indexByHeader, detectedHeaders);
            int lastNameCol = requireColumn(mapping.lastName(), indexByHeader, detectedHeaders);
            Integer phoneCol = phoneColumnIndex(mapping.phone(), indexByHeader, detectedHeaders);

            List<ParsedStudentRow> rows = new ArrayList<>();
            int lastRow = sheet.getLastRowNum();
            for (int r = 1; r <= lastRow; r++) {
                Row row = sheet.getRow(r);
                if (row == null || isRowBlank(row)) {
                    continue;
                }
                ParsedStudentRow parsed = parseRow(row, r + 1, emailCol, firstNameCol, lastNameCol, phoneCol);
                rows.add(parsed);
            }
            return rows;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read XLSX file", e);
        }
    }

    private ParsedStudentRow parseRow(Row row, int rowNumber, int emailCol, int firstNameCol,
                                      int lastNameCol, Integer phoneCol) {
        List<String> errors = new ArrayList<>();
        String email = readRequired(row, emailCol, "email", errors);
        String firstName = readRequired(row, firstNameCol, "firstName", errors);
        String lastName = readRequired(row, lastNameCol, "lastName", errors);
        String phone = null;
        if (phoneCol != null) {
            String rawPhone = readCell(row, phoneCol);
            if (rawPhone != null) {
                try {
                    phone = phoneNumberNormalizer.normalizeUa(rawPhone);
                } catch (InvalidPhoneNumberException ex) {
                    phone = rawPhone;
                    errors.add("phone: " + ex.getMessage());
                }
            }
        }
        return new ParsedStudentRow(rowNumber, email, firstName, lastName, phone, errors);
    }

    private String readRequired(Row row, int col, String field, List<String> errors) {
        String value = readCell(row, col);
        if (value == null) {
            errors.add(field + ": is required");
        }
        return value;
    }

    private String readCell(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) {
            return null;
        }
        String raw = DATA_FORMATTER.formatCellValue(cell);
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isRowBlank(Row row) {
        short last = row.getLastCellNum();
        for (int c = 0; c < last; c++) {
            String v = readCell(row, c);
            if (v != null) {
                return false;
            }
        }
        return true;
    }

    private List<String> collectHeaders(Sheet sheet) {
        Row header = sheet.getRow(0);
        if (header == null) {
            return List.of();
        }
        List<String> headers = new ArrayList<>();
        short last = header.getLastCellNum();
        for (int c = 0; c < last; c++) {
            String value = readCell(header, c);
            if (value != null) {
                headers.add(value);
            }
        }
        return headers;
    }

    /**
     * Builds a canonical (lower-cased, trimmed) header→columnIndex lookup.
     * Uses a LinkedHashMap to preserve first-occurrence wins on duplicates
     * (only the first index for a repeated header is kept).
     */
    private Map<String, Integer> buildHeaderIndex(Sheet sheet) {
        Row header = sheet.getRow(0);
        Map<String, Integer> map = new LinkedHashMap<>();
        if (header == null) {
            return map;
        }
        short last = header.getLastCellNum();
        for (int c = 0; c < last; c++) {
            String value = readCell(header, c);
            if (value == null) {
                continue;
            }
            String key = value.toLowerCase();
            map.putIfAbsent(key, c);
        }
        return map;
    }

    private int requireColumn(String header, Map<String, Integer> index, List<String> detectedHeaders) {
        String key = header == null ? "" : header.trim().toLowerCase();
        Integer col = index.get(key);
        if (col == null) {
            throw new MissingColumnException(header, detectedHeaders);
        }
        return col;
    }

    private Integer phoneColumnIndex(String header, Map<String, Integer> index, List<String> detectedHeaders) {
        if (header == null || header.isBlank()) {
            return null;
        }
        return requireColumn(header, index, detectedHeaders);
    }
}
