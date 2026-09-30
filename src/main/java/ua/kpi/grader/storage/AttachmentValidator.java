package ua.kpi.grader.storage;

import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;

public final class AttachmentValidator {

    public static final long MAX_FILE_BYTES = 20L * 1024 * 1024;
    public static final int MAX_FILES_PER_REQUEST = 5;

    public static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "doc", "docx", "txt", "md", "zip",
            "png", "jpg", "jpeg", "csv", "xlsx"
    );

    private AttachmentValidator() {
    }

    /**
     * Returns a filesystem-safe filename derived from the multipart original filename.
     * Strips any directory components to prevent path traversal.
     */
    public static String safeFilename(MultipartFile file) {
        String original = file.getOriginalFilename();
        if (original == null || original.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attachment must have a filename");
        }
        Path leaf = Paths.get(original).getFileName();
        if (leaf == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid attachment filename");
        }
        return leaf.toString();
    }

    /**
     * Validates a single upload: filename, extension whitelist, size ceiling.
     *
     * @return the sanitised filename ready for storage-key composition
     * @throws ResponseStatusException with 413 (too large) or 415 (unsupported type) on failure
     */
    public static String validate(MultipartFile file) {
        String filename = safeFilename(file);
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attachment " + filename + " is empty");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "Attachment " + filename + " exceeds 20MB limit");
        }
        String extension = extensionOf(filename);
        if (extension == null || !ALLOWED_EXTENSIONS.contains(extension)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Attachment " + filename + " has unsupported type");
        }
        return filename;
    }

    /**
     * Enforces the per-request file count ceiling.
     */
    public static void ensureBatchLimit(int count) {
        if (count == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No files supplied");
        }
        if (count > MAX_FILES_PER_REQUEST) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "At most " + MAX_FILES_PER_REQUEST + " files per upload");
        }
    }

    private static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return null;
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
