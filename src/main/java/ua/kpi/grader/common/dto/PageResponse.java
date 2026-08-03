package ua.kpi.grader.common.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Stable JSON wrapper for a Spring Data page. Insulates the API contract from
 * Spring's internal Page serialization (which may change between versions).
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
