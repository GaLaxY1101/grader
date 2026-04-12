package ua.kpi.grader.course.dto;

import ua.kpi.grader.course.entity.FileUploadTask;

import java.util.List;

public record FileUploadTaskDetails(
        List<String> allowedExtensions,
        Integer maxFileSize,
        Integer allowedFileCount
) {
    public static FileUploadTaskDetails from(FileUploadTask task) {
        if (task == null) return null;
        return new FileUploadTaskDetails(
                task.getAllowedExtensions(),
                task.getMaxFileSize(),
                task.getAllowedFileCount()
        );
    }
}
