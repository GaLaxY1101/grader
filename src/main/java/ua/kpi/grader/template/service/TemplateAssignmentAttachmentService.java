package ua.kpi.grader.template.service;

import org.springframework.web.multipart.MultipartFile;
import ua.kpi.grader.storage.dto.AttachmentSummary;
import ua.kpi.grader.storage.dto.UploadResponse;

import java.net.URI;
import java.util.List;

public interface TemplateAssignmentAttachmentService {

    /**
     * Uploads one or more attachments to a template assignment.
     */
    UploadResponse upload(Long templateAssignmentId, List<MultipartFile> files);

    /**
     * Lists all attachments on a template assignment, newest first.
     */
    List<AttachmentSummary> list(Long templateAssignmentId);

    /**
     * Deletes an attachment (row + blob).
     */
    void delete(Long templateAssignmentId, Long attachmentId);

    /**
     * Generates a short-lived pre-signed download URL.
     */
    URI download(Long templateAssignmentId, Long attachmentId);
}
