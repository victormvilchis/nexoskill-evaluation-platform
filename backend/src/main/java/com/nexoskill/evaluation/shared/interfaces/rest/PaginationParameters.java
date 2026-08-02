package com.nexoskill.evaluation.shared.interfaces.rest;

import java.util.Map;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Adaptador de compatibilidad para controladores existentes.
 * La regla de paginación se encuentra en la capa de aplicación.
 */
public final class PaginationParameters {
    public static final int DEFAULT_PAGE =
            com.nexoskill.evaluation.shared.application.PaginationParameters.DEFAULT_PAGE;
    public static final int DEFAULT_SIZE =
            com.nexoskill.evaluation.shared.application.PaginationParameters.DEFAULT_SIZE;
    public static final int MAX_SIZE =
            com.nexoskill.evaluation.shared.application.PaginationParameters.MAX_SIZE;

    private PaginationParameters() {}

    public static void validate(int page, int size) {
        com.nexoskill.evaluation.shared.application.PaginationParameters.validate(page, size);
    }

    public static Pageable of(
            int page,
            int size,
            String requestedSort,
            String requestedDirection,
            Map<String, String> allowedSorts,
            String defaultSort,
            Sort.Direction defaultDirection,
            String stableProperty) {
        return com.nexoskill.evaluation.shared.application.PaginationParameters.of(
                page,
                size,
                requestedSort,
                requestedDirection,
                allowedSorts,
                defaultSort,
                defaultDirection,
                stableProperty);
    }
}
