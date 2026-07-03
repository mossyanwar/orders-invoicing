package com.mossyanwar.ordersinvoicing.common.web;

import org.springframework.data.domain.Page;

import java.util.List;

/** A stable JSON shape for paged results, instead of serialising Spring's {@code PageImpl}. */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
