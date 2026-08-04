package com.nexoskill.evaluation.forms.application;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class FormModels {
	private FormModels() {
	}

	public record FormCommand(String title, String description, String modeCode, BigDecimal passingScore,
			Integer maxAttempts, boolean retryUntilPassed, boolean acceptResponses, OffsetDateTime startsAt,
			OffsetDateTime endsAt, Integer durationMinutes, boolean showResults, boolean showCorrectAnswers,
			boolean randomizeQuestions, boolean randomizeOptions, boolean showProgress, boolean hideQuestionNumbers,
			boolean allowSaveResume, boolean oneActiveAttempt, String thankYouMessage, Long version,
			String contentScope, String organizationPublicId, String contentMode, String operationId,
			List<QuestionItem> questions, List<PoolItem> pools, List<SectionCommand> sections) {
	}

	/**
	 * Compatibilidad de lectura con formularios construidos antes de la
	 * homologación del contenido.
	 */
	public record SectionCommand(String publicId, String title, String description, Integer order,
			List<QuestionItem> questions, List<PoolItem> pools) {
	}

	public record QuestionItem(String questionPublicId, Integer order, BigDecimal points, boolean required) {
	}

	public record PoolItem(String publicId, String sourceType, String sourcePublicId, Integer questionCount,
			String difficultyCode, Integer order) {
	}

	public record CloneCommand(String title, String targetScope, String organizationPublicId, String operationId) {
	}

	public record FormSummary(String publicId, String code, String title, String status, String modeCode,
			BigDecimal passingScore, String contentScope, String ownerOrganizationPublicId,
			String ownerOrganizationName, String contentMode, Integer questionCount, Integer poolCount,
			OffsetDateTime startsAt, OffsetDateTime endsAt, Long version) {
	}

	public record FormDetail(String publicId, String code, String title, String description, String status,
			String modeCode, BigDecimal passingScore, Integer maxAttempts, boolean retryUntilPassed,
			boolean acceptResponses, OffsetDateTime startsAt, OffsetDateTime endsAt, Integer durationMinutes,
			boolean showResults, boolean showCorrectAnswers, boolean randomizeQuestions, boolean randomizeOptions,
			boolean showProgress, boolean hideQuestionNumbers, boolean allowSaveResume, boolean oneActiveAttempt,
			String thankYouMessage, Long version, String contentScope, String ownerOrganizationPublicId,
			String ownerOrganizationName, String contentMode, List<QuestionView> questions, List<PoolView> pools) {
		public FormDetail {
			questions = questions == null ? List.of() : List.copyOf(questions);
			pools = pools == null ? List.of() : List.copyOf(pools);
		}
	}

	public record QuestionView(String questionPublicId, String statement, String typeCode, String typeName,
			String difficultyCode, String difficultyName, String technologyName, String levelCode,
			List<String> categoryNames, String status, Integer order, BigDecimal points, boolean required) {
		public QuestionView {
			categoryNames = categoryNames == null ? List.of() : List.copyOf(categoryNames);
		}
	}

	public record PoolView(String publicId, String categoryPublicId, String categoryName, Integer questionCount,
			String difficultyCode, Integer order, Integer availableQuestionCount) {
	}

	public record QuestionAnswerOption(String publicId, Integer order, String text, String matchText, boolean correct,
			String feedback) {
	}

	public record QuestionOptionView(String publicId, String statement, String typeCode, String typeName,
			String difficultyCode, String difficultyName, String technologyName, String levelCode,
			List<String> categoryNames, String contentScope, String organizationName, String explanation,
			String codeLanguage, String codeContent, String acceptedAnswersJson, List<QuestionAnswerOption> options,
			BigDecimal defaultPoints) {
		public QuestionOptionView {
			categoryNames = categoryNames == null ? List.of() : List.copyOf(categoryNames);
			options = options == null ? List.of() : List.copyOf(options);
		}
	}

	public record QuestionOptionPage(List<QuestionOptionView> content, int page, int size, long totalElements,
			int totalPages) {
		public QuestionOptionPage {
			content = content == null ? List.of() : List.copyOf(content);
		}
	}

	public record OrganizationOptionView(String publicId, String code, String name) {
	}

	public record CategoryOptionView(String publicId, String code, String name, String contentScope,
			String organizationName, Integer activeQuestionCount) {
	}
}
