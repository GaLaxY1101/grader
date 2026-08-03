package ua.kpi.grader.grade.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import ua.kpi.grader.course.dto.CourseGradesResponse;
import ua.kpi.grader.course.dto.CourseGradesResponse.AssignmentGradeSummary;
import ua.kpi.grader.course.dto.CourseGradesResponse.StudentGradeCell;
import ua.kpi.grader.course.dto.CourseGradesResponse.StudentGradesRow;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

@Component
class GradesXlsxWriter {

    /**
     * Serializes the gradebook as an XLSX workbook (single sheet).
     * First row bold header, per-student rows below, final Total column with sum/maxTotal.
     */
    byte[] write(CourseGradesResponse gradebook) {
        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet(safeSheetName(gradebook.courseName()));
            CellStyle headerStyle = headerStyle(wb);

            Row header = sheet.createRow(0);
            int col = 0;
            header.createCell(col++).setCellValue("Last name");
            header.createCell(col++).setCellValue("First name");
            header.createCell(col++).setCellValue("Email");
            for (AssignmentGradeSummary a : gradebook.assignments()) {
                Cell c = header.createCell(col++);
                c.setCellValue(a.title() + " (/" + a.maxScore() + ")");
            }
            header.createCell(col).setCellValue("Total");
            int lastCol = col;
            for (int i = 0; i <= lastCol; i++) {
                header.getCell(i).setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            for (StudentGradesRow row : gradebook.students()) {
                Row r = sheet.createRow(rowIdx++);
                int c = 0;
                r.createCell(c++).setCellValue(nullToEmpty(row.lastName()));
                r.createCell(c++).setCellValue(nullToEmpty(row.firstName()));
                r.createCell(c++).setCellValue(nullToEmpty(row.email()));
                for (StudentGradeCell cell : row.grades()) {
                    Cell gc = r.createCell(c++);
                    if (cell.grade() != null) {
                        gc.setCellValue(cell.grade());
                    } else if (cell.status() != null) {
                        gc.setCellValue("(" + cell.status() + ")");
                    }
                }
                r.createCell(c).setCellValue(row.total() + "/" + row.maxTotal());
            }

            for (int i = 0; i <= lastCol; i++) {
                sheet.autoSizeColumn(i);
            }
            sheet.setAutoFilter(new CellRangeAddress(0, Math.max(0, rowIdx - 1), 0, lastCol));

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to render XLSX gradebook", e);
        }
    }

    private static CellStyle headerStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private static String safeSheetName(String name) {
        String base = (name == null || name.isBlank()) ? "Grades" : name;
        String trimmed = base.replaceAll("[\\[\\]*/:?\\\\]", "").trim();
        if (trimmed.length() > 31) trimmed = trimmed.substring(0, 31);
        return trimmed.isBlank() ? "Grades" : trimmed;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
