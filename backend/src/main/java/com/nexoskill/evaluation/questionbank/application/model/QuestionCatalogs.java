package com.nexoskill.evaluation.questionbank.application.model;

import java.util.List;

public record QuestionCatalogs(List<CatalogOption> types, List<CatalogOption> difficulties,
		List<QuestionTechnologySummary> technologies, List<QuestionCategorySummary> categories) {
	public QuestionCatalogs {
		types = List.copyOf(types);
		difficulties = List.copyOf(difficulties);
		technologies = List.copyOf(technologies);
		categories = List.copyOf(categories);
	}
}
