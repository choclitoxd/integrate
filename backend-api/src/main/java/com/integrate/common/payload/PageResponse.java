package com.integrate.common.payload;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * Página del contrato (ADR-0006 §6): {@code {items, page, size, totalElements, totalPages}}. No expone
 * el {@code Page} de Spring Data, cuyo formato JSON no es estable entre versiones.
 */
public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 50;

    public PageResponse {
        items = List.copyOf(items);
    }

    public static <E, T> PageResponse<T> of(Page<E> page, Function<? super E, ? extends T> mapper) {
        return from(page, page.getContent().stream().<T>map(mapper).toList());
    }

    /** Para cuando el mapeo se hace por lotes (evita N+1). */
    public static <T> PageResponse<T> from(Page<?> page, List<T> items) {
        return new PageResponse<>(items, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
