package ua.kpi.grader.course.service;

import org.springframework.web.multipart.MultipartFile;
import ua.kpi.grader.storage.dto.AttachmentSummary;
import ua.kpi.grader.storage.dto.UploadResponse;

import java.net.URI;
import java.util.List;

public interface AssignmentAttachmentService {

    /**
     * Uploads one or more teacher attachments for the given assignment.
     */
    UploadResponse upload(Long assignmentId, List<MultipartFile> files);

    /**
     * Lists all attachments on an assignment, newest first.
     */
    List<AttachmentSummary> list(Long assignmentId);

    /**
     * Deletes an attachment (both the DB row and the underlying blob).
     */
    void delete(Long assignmentId, Long attachmentId);

    /**
     * Generates a short-lived pre-signed download URL for an attachment.
     */
    URI download(Long assignmentId, Long attachmentId);
}
