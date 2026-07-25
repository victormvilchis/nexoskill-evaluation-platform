package com.nexoskill.evaluation.questionbank.application.model;

import java.math.BigDecimal;
import java.util.List;

public record QuestionAnswerSettings(List<String> acceptedAnswers, boolean caseSensitive, boolean manualReview,
		BigDecimal numericMin, BigDecimal numericMax, BigDecimal numericTolerance, Integer maxLength) {
	public QuestionAnswerSettings {
		acceptedAnswers = acceptedAnswers == null ? List.of() : List.copyOf(acceptedAnswers);
	}

	public static QuestionAnswerSettings empty() {
		return new QuestionAnswerSettings(List.of(), false, false, null, null, null, null);
	}
}
