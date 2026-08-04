package com.nexoskill.evaluation.shared.interfaces.rest;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;

public record PagedResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages, boolean first,
		boolean last, boolean hasNext, boolean hasPrevious, SortMetadata sort) {

	public static <S, T> PagedResponse<T> from(Page<S> source, Function<S, T> mapper) {
		Sort.Order order = source.getSort().stream().findFirst().orElse(null);
		SortMetadata sortMetadata = order == null ? null
				: new SortMetadata(order.getProperty(), order.getDirection().name());
		return new PagedResponse<>(source.getContent().stream().map(mapper).toList(), source.getNumber(),
				source.getSize(), source.getTotalElements(), source.getTotalPages(), source.isFirst(), source.isLast(),
				source.hasNext(), source.hasPrevious(), sortMetadata);
	}

	public record SortMetadata(String property, String direction) {
	}
}
