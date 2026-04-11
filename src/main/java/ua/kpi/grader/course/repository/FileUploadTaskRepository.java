package ua.kpi.grader.course.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ua.kpi.grader.course.entity.FileUploadTask;

public interface FileUploadTaskRepository extends JpaRepository<FileUploadTask, Long> {
}
