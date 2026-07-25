package com.nexoskill.evaluation.questionbank.application.model;

import com.nexoskill.evaluation.questionbank.domain.model.CatalogStatus;
import java.time.Instant;

public record QuestionCategorySummary(String publicId, String code, String name, String description,
		CatalogStatus status, long entityVersion, long questionCount, Instant createdAt, Instant updatedAt) {
}
