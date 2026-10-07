package com.integrate.common.web;

import com.integrate.common.payload.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Paginación común (ADR-0006 §6): {@code ?page=0&size=20}, máximo 50. El orden lo fija el servidor
 * ({@code created_at DESC, id}) para que las páginas sean estables; el cliente no lo elige.
 */
public final class PageRequests {

    public static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"));

    private PageRequests() {
    }

    /** {@code page} y {@code size} ya validados en el controlador ({@code @Min}/{@code @Max}). */
    public static Pageable newestFirst(Integer page, Integer size) {
        return PageRequest.of(page == null ? 0 : page,
                size == null ? PageResponse.DEFAULT_SIZE : Math.min(size, PageResponse.MAX_SIZE), NEWEST_FIRST);
    }
}
