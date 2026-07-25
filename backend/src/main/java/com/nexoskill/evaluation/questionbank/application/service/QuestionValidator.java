package com.nexoskill.evaluation.questionbank.application.service;

import com.nexoskill.evaluation.questionbank.application.model.QuestionAnswerSettings;
import com.nexoskill.evaluation.questionbank.application.model.QuestionOptionCommand;
import com.nexoskill.evaluation.questionbank.domain.model.QuestionTypeCode;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public class QuestionValidator {

	private static final int MAX_OPTIONS = 20;
	private static final int MAX_ACCEPTED_ANSWERS = 50;
	private static final int MAX_ACCEPTED_ANSWER_LENGTH = 2_000;
	private static final int MAX_RESPONSE_LENGTH = 100_000;

	public void validate(QuestionTypeCode type, String statement, List<String> categories,
			QuestionAnswerSettings settings, List<QuestionOptionCommand> options) {

		if (statement == null || statement.isBlank()) {
			throw error("QUESTION_STATEMENT_REQUIRED", "El enunciado de la pregunta es obligatorio.");
		}
		if (statement.trim().length() > 10_000) {
			throw error("QUESTION_STATEMENT_TOO_LONG", "El enunciado no puede superar 10,000 caracteres.");
		}
		if (categories == null || categories.isEmpty()) {
			throw error("QUESTION_CATEGORY_REQUIRED", "Selecciona al menos una categoría.");
		}
		if (categories.stream().anyMatch(value -> value == null || value.isBlank())) {
			throw error("QUESTION_CATEGORY_INVALID", "Una categoría seleccionada no es válida.");
		}
		if (new HashSet<>(categories).size() != categories.size()) {
			throw error("QUESTION_CATEGORY_DUPLICATED", "No repitas categorías dentro de la pregunta.");
		}

		validateCommonAnswerSettings(settings);

		if (type.usesOptions()) {
			validateOptions(type, options);
		} else {
			validateOpen(type, settings, options);
		}
	}

	private void validateCommonAnswerSettings(QuestionAnswerSettings settings) {
		Integer maxLength = settings.maxLength();
		if (maxLength != null && (maxLength < 1 || maxLength > MAX_RESPONSE_LENGTH)) {
			throw error("QUESTION_RESPONSE_LENGTH_INVALID",
					"La longitud máxima de respuesta debe estar entre 1 y 100,000 caracteres.");
		}
		if (settings.acceptedAnswers().size() > MAX_ACCEPTED_ANSWERS) {
			throw error("QUESTION_ACCEPTED_ANSWERS_LIMIT", "No puedes registrar más de 50 respuestas aceptadas.");
		}
	}

	private void validateOptions(QuestionTypeCode type, List<QuestionOptionCommand> options) {
		if (options == null || options.size() < 2 || options.size() > MAX_OPTIONS) {
			throw error("QUESTION_OPTIONS_INVALID", "La pregunta debe contener entre 2 y 20 opciones.");
		}

		Set<String> seen = new HashSet<>();
		long correct = 0;

		for (QuestionOptionCommand option : options) {
			boolean hasText = option.text() != null && !option.text().isBlank();
			boolean hasMedia = option.mediaPublicId() != null && !option.mediaPublicId().isBlank();

			if (!hasText && !hasMedia) {
				throw error("QUESTION_OPTION_CONTENT_REQUIRED", "Cada opción debe tener texto, imagen o ambos.");
			}
			if (hasText && option.text().trim().length() > 2_000) {
				throw error("QUESTION_OPTION_TOO_LONG", "Una opción no puede superar 2,000 caracteres.");
			}

			String key = (hasText ? normalize(option.text()) : "") + "|"
					+ (hasMedia ? option.mediaPublicId().trim().toLowerCase(Locale.ROOT) : "");
			if (!seen.add(key)) {
				throw error("DUPLICATE_OPTION", "La pregunta contiene opciones duplicadas.");
			}
			if (option.correct()) {
				correct++;
			}
		}

		if (type == QuestionTypeCode.MULTIPLE_CHOICE) {
			if (options.size() < 3 || correct < 2 || correct >= options.size()) {
				throw error("QUESTION_MULTIPLE_CORRECT_INVALID",
						"La opción múltiple requiere al menos tres opciones, dos correctas y una incorrecta.");
			}
		} else if (correct != 1) {
			throw error("QUESTION_SINGLE_CORRECT_REQUIRED", "Selecciona exactamente una respuesta correcta.");
		}

		if (type == QuestionTypeCode.TRUE_FALSE) {
			if (options.size() != 2) {
				throw error("QUESTION_TRUE_FALSE_INVALID", "Verdadero o falso requiere exactamente dos opciones.");
			}
			Set<String> values = options.stream().map(QuestionOptionCommand::text).filter(Objects::nonNull)
					.map(this::normalize).collect(java.util.stream.Collectors.toSet());
			if (!values.equals(Set.of("verdadero", "falso"))) {
				throw error("QUESTION_TRUE_FALSE_OPTIONS_INVALID", "Las opciones deben ser Verdadero y Falso.");
			}
		}
	}

	private void validateOpen(QuestionTypeCode type, QuestionAnswerSettings settings,
			List<QuestionOptionCommand> options) {

		if (options != null && !options.isEmpty()) {
			throw error("QUESTION_OPTIONS_NOT_ALLOWED", "Este tipo de pregunta no utiliza opciones.");
		}
		if (type.requiresManualReview() && !settings.manualReview()) {
			throw error("QUESTION_MANUAL_REVIEW_REQUIRED", "Este tipo de pregunta requiere revisión manual.");
		}
		if (type == QuestionTypeCode.SHORT_TEXT && !settings.manualReview() && settings.acceptedAnswers().isEmpty()) {
			throw error("QUESTION_ACCEPTED_ANSWER_REQUIRED",
					"Agrega al menos una respuesta aceptada o activa la revisión manual.");
		}
		if (type == QuestionTypeCode.NUMBER && !settings.manualReview() && settings.numericMin() == null
				&& settings.numericMax() == null) {
			throw error("QUESTION_NUMERIC_RULE_REQUIRED",
					"Configura un valor o rango numérico, o activa la revisión manual.");
		}
		if (settings.numericMin() != null && settings.numericMax() != null
				&& settings.numericMin().compareTo(settings.numericMax()) > 0) {
			throw error("QUESTION_NUMERIC_RANGE_INVALID", "El valor mínimo no puede ser mayor que el máximo.");
		}
		if (settings.numericTolerance() != null && settings.numericTolerance().signum() < 0) {
			throw error("QUESTION_NUMERIC_TOLERANCE_INVALID", "La tolerancia no puede ser negativa.");
		}

		Set<String> answers = new HashSet<>();
		for (String answer : settings.acceptedAnswers()) {
			if (answer == null || answer.isBlank()) {
				throw error("QUESTION_ACCEPTED_ANSWER_EMPTY", "Las respuestas aceptadas no pueden estar vacías.");
			}
			if (answer.trim().length() > MAX_ACCEPTED_ANSWER_LENGTH) {
				throw error("QUESTION_ACCEPTED_ANSWER_TOO_LONG",
						"Una respuesta aceptada no puede superar 2,000 caracteres.");
			}
			if (!answers.add(normalize(answer))) {
				throw error("QUESTION_ACCEPTED_ANSWER_DUPLICATED", "No repitas respuestas aceptadas.");
			}
		}
	}

	private String normalize(String value) {
		return Normalizer.normalize(value.trim(), Normalizer.Form.NFKC).replaceAll("\\s+", " ")
				.toLowerCase(Locale.ROOT);
	}

	private BusinessException error(String code, String message) {
		return new BusinessException(code, message);
	}
}
