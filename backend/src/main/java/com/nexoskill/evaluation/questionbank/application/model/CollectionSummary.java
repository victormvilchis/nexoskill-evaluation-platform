package com.nexoskill.evaluation.questionbank.application.model;

import com.nexoskill.evaluation.questionbank.domain.model.CollectionStatus;
import java.time.Instant;

public record CollectionSummary(String publicId, String name, String description, CollectionStatus status,
		int categoryCount, int explicitQuestionCount, int effectiveQuestionCount, long entityVersion, Instant createdAt,
		Instant updatedAt) {
}
