package ua.kpi.grader.user.service;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import ua.kpi.grader.common.exception.MissingColumnException;
import ua.kpi.grader.common.util.PhoneNumberNormalizer;
import ua.kpi.grader.user.dto.ParsedStudentRow;
import ua.kpi.grader.user.dto.StudentColumnMapping;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StudentXlsxReaderTest {

    private final StudentXlsxReader reader = new StudentXlsxReader(new PhoneNumberNormalizer());

    private static final StudentColumnMapping CANONICAL = new StudentColumnMapping(
            "email", "firstName", "lastName", "phone");

    // --- headers ---

    @Test
    void readHeaders_returnsFirstRowValues_inOrder() throws IOException {
        byte[] file = xlsx(sheet -> {
            writeRow(sheet, 0, "Email", "First Name", "Surname", "Телефон");
            writeRow(sheet, 1, "alice@example.com", "Alice", "Smith", "0508529087");
        });
        assertThat(reader.readHeaders(new ByteArrayInputStream(file)))
                .containsExactly("Email", "First Name", "Surname", "Телефон");
    }

    // --- mapping / column resolution ---

    @Test
    void read_resolvesCustomHeaderNames_withMapping() throws IOException {
        byte[] file = xlsx(sheet -> {
            writeRow(sheet, 0, "E-mail", "First Name", "Surname", "Телефон");
            writeRow(sheet, 1, "alice@example.com", "Alice", "Smith", "+380508529087");
        });
        StudentColumnMapping mapping = new StudentColumnMapping(
                "E-mail", "First Name", "Surname", "Телефон");

        List<ParsedStudentRow> rows = reader.read(new ByteArrayInputStream(file), mapping);

        assertThat(rows).hasSize(1);
        ParsedStudentRow row = rows.get(0);
        assertThat(row.email()).isEqualTo("alice@example.com");
        assertThat(row.firstName()).isEqualTo("Alice");
        assertThat(row.lastName()).isEqualTo("Smith");
        assertThat(row.phone()).isEqualTo("+380508529087");
        assertThat(row.errors()).isEmpty();
    }

    @Test
    void read_matchesHeaders_caseInsensitively_andTrimsWhitespace() throws IOException {
        byte[] file = xlsx(sheet -> {
            writeRow(sheet, 0, "  EMAIL ", "firstname", "LASTNAME");
            writeRow(sheet, 1, "bob@example.com", "Bob", "Jones");
        });
        StudentColumnMapping mapping = new StudentColumnMapping(
                "Email", "FirstName", "LastName", null);

        List<ParsedStudentRow> rows = reader.read(new ByteArrayInputStream(file), mapping);

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.email()).isEqualTo("bob@example.com");
            assertThat(row.firstName()).isEqualTo("Bob");
            assertThat(row.lastName()).isEqualTo("Jones");
            assertThat(row.phone()).isNull();
            assertThat(row.errors()).isEmpty();
        });
    }

    @Test
    void read_throwsMissingColumnException_whenRequiredHeaderAbsent() throws IOException {
        byte[] file = xlsx(sheet -> {
            writeRow(sheet, 0, "email", "firstName");
            writeRow(sheet, 1, "alice@example.com", "Alice");
        });

        assertThatThrownBy(() -> reader.read(new ByteArrayInputStream(file), CANONICAL))
                .isInstanceOfSatisfying(MissingColumnException.class, ex -> {
                    assertThat(ex.getMissingHeader()).isEqualTo("lastName");
                    assertThat(ex.getDetectedHeaders()).containsExactly("email", "firstName");
                });
    }

    @Test
    void read_throwsMissingColumnException_whenMappedPhoneHeaderAbsent() throws IOException {
        byte[] file = xlsx(sheet -> {
            writeRow(sheet, 0, "email", "firstName", "lastName");
            writeRow(sheet, 1, "alice@example.com", "Alice", "Smith");
        });
        StudentColumnMapping mapping = new StudentColumnMapping(
                "email", "firstName", "lastName", "phone");

        assertThatThrownBy(() -> reader.read(new ByteArrayInputStream(file), mapping))
                .isInstanceOfSatisfying(MissingColumnException.class, ex ->
                        assertThat(ex.getMissingHeader()).isEqualTo("phone"));
    }

    // --- phone parsing ---

    @Test
    void read_normalisesPhone_toE164() throws IOException {
        byte[] file = xlsx(sheet -> {
            writeRow(sheet, 0, "email", "firstName", "lastName", "phone");
            writeRow(sheet, 1, "a@x.com", "A", "One",   "0508529087");
            writeRow(sheet, 2, "b@x.com", "B", "Two",   "+38 (050) 852-9088");
            writeRow(sheet, 3, "c@x.com", "C", "Three", "380508529089");
        });

        List<ParsedStudentRow> rows = reader.read(new ByteArrayInputStream(file), CANONICAL);

        assertThat(rows).extracting(ParsedStudentRow::phone)
                .containsExactly("+380508529087", "+380508529088", "+380508529089");
    }

    @Test
    void read_recordsPhoneError_andKeepsRawValue_whenPhoneUnparseable() throws IOException {
        byte[] file = xlsx(sheet -> {
            writeRow(sheet, 0, "email", "firstName", "lastName", "phone");
            writeRow(sheet, 1, "alice@example.com", "Alice", "Smith", "not-a-phone");
        });

        List<ParsedStudentRow> rows = reader.read(new ByteArrayInputStream(file), CANONICAL);

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.phone()).isEqualTo("not-a-phone");
            assertThat(row.errors()).hasSize(1);
            assertThat(row.errors().get(0)).startsWith("phone:");
        });
    }

    @Test
    void read_setsPhoneToNull_whenMappingHasNoPhone_evenIfFileHasPhoneColumn() throws IOException {
        byte[] file = xlsx(sheet -> {
            writeRow(sheet, 0, "email", "firstName", "lastName", "phone");
            writeRow(sheet, 1, "alice@example.com", "Alice", "Smith", "0508529087");
        });
        StudentColumnMapping mapping = new StudentColumnMapping(
                "email", "firstName", "lastName", null);

        List<ParsedStudentRow> rows = reader.read(new ByteArrayInputStream(file), mapping);

        assertThat(rows).singleElement().satisfies(row -> assertThat(row.phone()).isNull());
    }

    // --- rows ---

    @Test
    void read_skipsBlankRows() throws IOException {
        byte[] file = xlsx(sheet -> {
            writeRow(sheet, 0, "email", "firstName", "lastName");
            writeRow(sheet, 1, "alice@example.com", "Alice", "Smith");
            // row 2 intentionally left blank
            sheet.createRow(2);
            writeRow(sheet, 3, "bob@example.com", "Bob", "Jones");
        });
        StudentColumnMapping mapping = new StudentColumnMapping(
                "email", "firstName", "lastName", null);

        List<ParsedStudentRow> rows = reader.read(new ByteArrayInputStream(file), mapping);

        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(ParsedStudentRow::rowNumber).containsExactly(2, 4);
    }

    @Test
    void read_recordsRequiredError_whenRequiredCellBlank() throws IOException {
        byte[] file = xlsx(sheet -> {
            writeRow(sheet, 0, "email", "firstName", "lastName");
            writeRow(sheet, 1, "alice@example.com", "", "Smith");
        });
        StudentColumnMapping mapping = new StudentColumnMapping(
                "email", "firstName", "lastName", null);

        List<ParsedStudentRow> rows = reader.read(new ByteArrayInputStream(file), mapping);

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.firstName()).isNull();
            assertThat(row.errors()).hasSize(1);
            assertThat(row.errors().get(0)).startsWith("firstName:");
        });
    }

    // --- helpers ---

    private static byte[] xlsx(SheetBuilder builder) throws IOException {
        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet();
            builder.build(sheet);
            wb.write(out);
            return out.toByteArray();
        }
    }

    private static void writeRow(Sheet sheet, int rowIdx, String... values) {
        Row row = sheet.createRow(rowIdx);
        for (int c = 0; c < values.length; c++) {
            row.createCell(c).setCellValue(values[c]);
        }
    }

    @FunctionalInterface
    private interface SheetBuilder {
        void build(Sheet sheet);
    }
}
