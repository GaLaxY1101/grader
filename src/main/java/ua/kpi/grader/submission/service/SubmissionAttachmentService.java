package ua.kpi.grader.submission.service;

import org.springframework.web.multipart.MultipartFile;
import ua.kpi.grader.storage.dto.AttachmentSummary;
import ua.kpi.grader.storage.dto.UploadResponse;
import ua.kpi.grader.submission.dto.ReturnSubmissionRequest;
import ua.kpi.grader.submission.dto.SubmissionResponse;

import java.net.URI;
import java.util.List;

public interface SubmissionAttachmentService {

    /**
     * Uploads student attachments for the given assignment. Auto-creates the submission on the first
     * upload (state {@code DRAFT}). Rejects when the current file state locks further edits.
     *
     * @return an {@link UploadResponse} plus the (possibly newly-created) submission id in {@code submissionId}
     */
    SubmissionUploadResult upload(Long assignmentId, List<MultipartFile> files);

    List<AttachmentSummary> list(Long submissionId);

    void delete(Long submissionId, Long attachmentId);

    URI download(Long submissionId, Long attachmentId);

    /**
     * Transitions the workflow into {@code SUBMITTED}. Requires at least one attachment.
     */
    SubmissionResponse turnIn(Long submissionId);

    /**
     * Teacher returns the submission for redo. Grade is preserved (Google Classroom parity).
     */
    SubmissionResponse returnSubmission(Long submissionId, ReturnSubmissionRequest request);

    record SubmissionUploadResult(Long submissionId, UploadResponse upload) {}
}
