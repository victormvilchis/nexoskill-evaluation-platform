package com.nexoskill.evaluation.questionbank.application.model;

import com.nexoskill.evaluation.questionbank.domain.model.CollectionStatus;
import java.time.Instant;
import java.util.List;

public record CollectionDetail(String publicId, String name, String description, CollectionStatus status,
		List<QuestionCategoryRef> categories, List<QuestionSummary> explicitQuestions,
		List<QuestionSummary> effectiveQuestions, long entityVersion, Instant createdAt, Instant updatedAt) {
	public CollectionDetail {
		categories = List.copyOf(categories);
		explicitQuestions = List.copyOf(explicitQuestions);
		effectiveQuestions = List.copyOf(effectiveQuestions);
	}
}
