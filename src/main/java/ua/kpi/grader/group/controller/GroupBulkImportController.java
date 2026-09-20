package ua.kpi.grader.group.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import ua.kpi.grader.group.dto.BulkCommitRequest;
import ua.kpi.grader.group.dto.BulkCommitWithGroupRequest;
import ua.kpi.grader.group.dto.BulkImportResult;
import ua.kpi.grader.group.service.GroupBulkImportService;
import ua.kpi.grader.user.dto.ParsedStudentsResponse;
import ua.kpi.grader.user.dto.StudentColumnMapping;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class GroupBulkImportController {

    private static final String XLSX_MIME =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final GroupBulkImportService bulkImportService;

    @PostMapping(value = "/bulk-import/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ResponseEntity<ParsedStudentsResponse> parse(
            @RequestPart("meta") @Valid StudentColumnMapping mapping,
            @RequestPart("file") MultipartFile file) {
        requireXlsx(file);
        return ResponseEntity.ok(bulkImportService.parseFile(file, mapping));
    }

    @PostMapping(value = "/bulk-import", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ResponseEntity<BulkImportResult> commitWithNewGroup(
            @RequestBody @Valid BulkCommitWithGroupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(bulkImportService.commitWithNewGroup(request));
    }

    @PostMapping(value = "/{id}/students/bulk-import", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ResponseEntity<BulkImportResult> commitIntoGroup(
            @PathVariable Long id,
            @RequestBody @Valid BulkCommitRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(bulkImportService.commitIntoGroup(id, request));
    }

    private static void requireXlsx(MultipartFile file) {
        String contentType = file.getContentType();
        String filename = file.getOriginalFilename();
        boolean mimeOk = XLSX_MIME.equals(contentType);
        boolean extensionOk = filename != null && filename.toLowerCase().endsWith(".xlsx");
        if (!mimeOk && !extensionOk) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Uploaded file must be an .xlsx workbook");
        }
    }
}
