package ua.kpi.grader.submission.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ua.kpi.grader.submission.entity.SubmissionAttachment;

import java.util.List;
import java.util.Optional;

public interface SubmissionAttachmentRepository extends JpaRepository<SubmissionAttachment, Long> {

    List<SubmissionAttachment> findAllBySubmissionIdOrderByUploadedAtDesc(Long submissionId);

    Optional<SubmissionAttachment> findByIdAndSubmissionId(Long id, Long submissionId);

    long countBySubmissionId(Long submissionId);
}
