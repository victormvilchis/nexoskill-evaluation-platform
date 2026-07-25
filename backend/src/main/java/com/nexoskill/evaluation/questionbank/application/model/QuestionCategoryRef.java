package com.nexoskill.evaluation.questionbank.application.model;

import com.nexoskill.evaluation.questionbank.domain.model.CatalogStatus;

public record QuestionCategoryRef(String publicId, String code, String name, CatalogStatus status) {
}
