package com.nexoskill.evaluation.questionbank.application.model;

import java.util.List;

public record CollectionPage(List<CollectionSummary> content, int page, int size, long totalElements, int totalPages) {
	public CollectionPage {
		content = List.copyOf(content);
	}
}
