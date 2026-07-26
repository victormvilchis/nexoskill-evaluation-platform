package com.nexoskill.evaluation.questionbank.interfaces.rest;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public final class QuestionRequests {
	private QuestionRequests() {
	}

	public record Option(String text, String mediaPublicId, String matchText, String matchMediaPublicId,
			boolean correct, @Size(max = 4000) String feedback) {
	}

	public record AnswerSettings(List<String> acceptedAnswers, boolean caseSensitive, boolean manualReview,
			BigDecimal numericMin, BigDecimal numericMax, BigDecimal numericTolerance, Integer maxLength) {
	}

	public record Create(@NotBlank String typeCode, @NotEmpty List<String> categoryPublicIds,
			@NotBlank @Size(max = 10000) String statement, @Size(max = 10000) String explanation,
			String promptMediaPublicId, @Size(max = 30000) String codeContent, AnswerSettings answerSettings,
			List<Option> options) {
	}

	public record Update(@NotBlank String typeCode, @NotEmpty List<String> categoryPublicIds,
			@NotBlank @Size(max = 10000) String statement, @Size(max = 10000) String explanation,
			String promptMediaPublicId, @Size(max = 30000) String codeContent, AnswerSettings answerSettings,
			List<Option> options, @PositiveOrZero long expectedEntityVersion) {
	}

	public record ChangeStatus(@NotBlank String status, @PositiveOrZero long expectedEntityVersion) {
	}

	public record Delete(@Size(max = 500) String reason, @PositiveOrZero long expectedEntityVersion) {
	}

	public record CreateCategory(String code, @NotBlank @Size(max = 150) String name,
			@Size(max = 500) String description) {
	}

	public record UpdateCategory(String code, @NotBlank @Size(max = 150) String name,
			@Size(max = 500) String description, @PositiveOrZero long expectedEntityVersion) {
	}

	public record Status(@PositiveOrZero long expectedEntityVersion) {
	}

	public record CategoryStatus(@PositiveOrZero long expectedEntityVersion, @Size(max = 500) String reason) {
	}
}
