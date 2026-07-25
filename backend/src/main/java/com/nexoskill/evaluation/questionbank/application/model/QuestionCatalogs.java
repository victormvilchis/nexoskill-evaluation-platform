package com.nexoskill.evaluation.questionbank.application.model;

import java.util.List;

public record QuestionCatalogs(
        List<CatalogOption> types,
        List<QuestionCategorySummary> categories) {
    public QuestionCatalogs {
        types = List.copyOf(types);
        categories = List.copyOf(categories);
    }
}
