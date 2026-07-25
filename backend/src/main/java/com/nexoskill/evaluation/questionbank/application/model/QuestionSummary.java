package com.nexoskill.evaluation.questionbank.application.model;

import com.nexoskill.evaluation.questionbank.domain.model.QuestionStatus;
import java.time.Instant;
import java.util.List;

public record QuestionSummary(String publicId, String statement, String typeCode, String typeName,
		String difficultyCode, String difficultyName, List<QuestionCategoryRef> categories, QuestionStatus status,
		boolean hasMedia, Instant createdAt, Instant updatedAt) {
	public QuestionSummary {
		categories = List.copyOf(categories);
	}
}
