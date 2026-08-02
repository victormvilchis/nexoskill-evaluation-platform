package com.nexoskill.evaluation.shared.application;

import com.nexoskill.evaluation.shared.domain.BusinessException;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Reglas compartidas de paginación para casos de uso y adaptadores de entrada.
 *
 * <p>La validación vive en la capa de aplicación para evitar que los casos de uso
 * dependan de componentes REST.</p>
 */
public final class PaginationParameters {
    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 10;
    public static final int MAX_SIZE = 100;
    private static final Set<Integer> ALLOWED_SIZES = Set.of(10, 25, 50, 100);

    private PaginationParameters() {}

    public static void validate(int page, int size) {
        if (page < 0) {
            throw new BusinessException("PAGINATION_PAGE_INVALID", "La página solicitada no es válida.");
        }
        if (!ALLOWED_SIZES.contains(size) || size > MAX_SIZE) {
            throw new BusinessException(
                    "PAGINATION_SIZE_INVALID",
                    "El tamaño de página permitido es 10, 25, 50 o 100 registros.");
        }
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

        validate(page, size);
        String externalSort = requestedSort == null || requestedSort.isBlank()
                ? defaultSort
                : requestedSort.trim();
        String property = allowedSorts.get(externalSort);
        if (property == null) {
            throw new BusinessException("PAGINATION_SORT_INVALID", "La columna de ordenamiento no es válida.");
        }

        Sort.Direction direction = defaultDirection;
        if (requestedDirection != null && !requestedDirection.isBlank()) {
            try {
                direction = Sort.Direction.fromString(requestedDirection.trim());
            } catch (IllegalArgumentException exception) {
                throw new BusinessException(
                        "PAGINATION_DIRECTION_INVALID",
                        "La dirección de ordenamiento debe ser ASC o DESC.");
            }
        }

        Sort sort = Sort.by(new Sort.Order(direction, property));
        if (stableProperty != null && !stableProperty.isBlank() && !stableProperty.equals(property)) {
            sort = sort.and(Sort.by(new Sort.Order(direction, stableProperty)));
        }
        return PageRequest.of(page, size, sort);
    }
}
