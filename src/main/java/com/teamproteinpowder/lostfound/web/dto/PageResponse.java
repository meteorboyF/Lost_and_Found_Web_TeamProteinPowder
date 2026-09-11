package com.teamproteinpowder.lostfound.web.dto;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * A trimmed page envelope. Spring's own Page serialisation is unstable across
 * versions and leaks pageable internals, so the frontend gets exactly the four
 * numbers it needs to render a pager.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last) {

    public static <E, T> PageResponse<T> of(Page<E> page, java.util.function.Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }
}
