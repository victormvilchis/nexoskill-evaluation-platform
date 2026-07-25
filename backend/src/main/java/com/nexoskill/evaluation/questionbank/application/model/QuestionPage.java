package com.nexoskill.evaluation.questionbank.application.model;

import java.util.List;

public record QuestionPage(List<QuestionSummary> content, int page, int size, long totalElements, int totalPages) {
	public QuestionPage {
		content = List.copyOf(content);
	}
}
