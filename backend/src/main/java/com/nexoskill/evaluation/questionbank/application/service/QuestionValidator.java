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

    private static final int MAX_OPTIONS = 30;
    private static final int MAX_RESPONSE_LENGTH = 100_000;
    private static final int MAX_CODE_LENGTH = 30_000;

    public void validate(
            QuestionTypeCode type,
            String statement,
            List<String> categories,
            QuestionAnswerSettings settings,
            List<QuestionOptionCommand> options,
            String codeContent) {

        if (statement == null || statement.isBlank()) {
            throw error("QUESTION_STATEMENT_REQUIRED", "El enunciado de la pregunta es obligatorio.");
        }
        if (statement.trim().length() > 10_000) {
            throw error("QUESTION_STATEMENT_TOO_LONG", "El enunciado no puede superar 10,000 caracteres.");
        }
        if (codeContent != null && codeContent.length() > MAX_CODE_LENGTH) {
            throw error("QUESTION_CODE_TOO_LONG", "El bloque de código no puede superar 30,000 caracteres.");
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

        Integer maxLength = settings.maxLength();
        if (maxLength != null && (maxLength < 1 || maxLength > MAX_RESPONSE_LENGTH)) {
            throw error("QUESTION_RESPONSE_LENGTH_INVALID",
                    "La longitud máxima de respuesta debe estar entre 1 y 100,000 caracteres.");
        }

        if (type == QuestionTypeCode.OPEN_TEXT) {
            if (options != null && !options.isEmpty()) {
                throw error("QUESTION_OPTIONS_NOT_ALLOWED", "La respuesta abierta no utiliza opciones.");
            }
            return;
        }

        validateOptions(type, options);
    }

    private void validateOptions(QuestionTypeCode type, List<QuestionOptionCommand> options) {
        if (options == null || options.size() < 2 || options.size() > MAX_OPTIONS) {
            throw error("QUESTION_OPTIONS_INVALID", "La pregunta debe contener entre 2 y 30 opciones.");
        }

        Set<String> seen = new HashSet<>();
        Set<String> seenMatches = new HashSet<>();
        long correct = 0;

        for (QuestionOptionCommand option : options) {
            boolean hasText = hasValue(option.text());
            boolean hasMedia = hasValue(option.mediaPublicId());
            if (!hasText && !hasMedia) {
                throw error("QUESTION_OPTION_CONTENT_REQUIRED", "Cada opción debe tener texto, imagen o ambos.");
            }
            if (hasText && option.text().trim().length() > 2_000) {
                throw error("QUESTION_OPTION_TOO_LONG", "Una opción no puede superar 2,000 caracteres.");
            }
            if (hasValue(option.feedback()) && option.feedback().trim().length() > 4_000) {
                throw error("QUESTION_OPTION_FEEDBACK_TOO_LONG",
                        "La retroalimentación de una opción no puede superar 4,000 caracteres.");
            }

            String key = contentKey(option.text(), option.mediaPublicId());
            if (!seen.add(key)) {
                throw error("DUPLICATE_OPTION", "La pregunta contiene opciones duplicadas.");
            }

            if (type == QuestionTypeCode.MATCHING) {
                boolean hasMatchText = hasValue(option.matchText());
                boolean hasMatchMedia = hasValue(option.matchMediaPublicId());
                if (!hasMatchText && !hasMatchMedia) {
                    throw error("QUESTION_MATCH_CONTENT_REQUIRED",
                            "Cada fila de relación debe tener contenido en ambas columnas.");
                }
                if (hasMatchText && option.matchText().trim().length() > 2_000) {
                    throw error("QUESTION_MATCH_TOO_LONG",
                            "Un elemento de relación no puede superar 2,000 caracteres.");
                }
                String matchKey = contentKey(option.matchText(), option.matchMediaPublicId());
                if (!seenMatches.add(matchKey)) {
                    throw error("DUPLICATE_MATCH_OPTION",
                            "La columna derecha contiene elementos duplicados.");
                }
                continue;
            }

            if (option.correct()) correct++;
        }

        if (type == QuestionTypeCode.MULTIPLE_CHOICE) {
            if (options.size() < 3 || correct < 2 || correct >= options.size()) {
                throw error("QUESTION_MULTIPLE_CORRECT_INVALID",
                        "La opción múltiple requiere al menos tres opciones, dos correctas y una incorrecta.");
            }
        } else if (type == QuestionTypeCode.SINGLE_CHOICE && correct != 1) {
            throw error("QUESTION_SINGLE_CORRECT_REQUIRED", "Selecciona exactamente una respuesta correcta.");
        }

        if (type == QuestionTypeCode.TRUE_FALSE) {
            if (options.size() != 2 || correct != 1) {
                throw error("QUESTION_TRUE_FALSE_INVALID",
                        "Verdadero o falso requiere exactamente dos opciones y una respuesta correcta.");
            }
            Set<String> values = options.stream().map(QuestionOptionCommand::text).filter(Objects::nonNull)
                    .map(this::normalize).collect(java.util.stream.Collectors.toSet());
            if (!values.equals(Set.of("verdadero", "falso"))) {
                throw error("QUESTION_TRUE_FALSE_OPTIONS_INVALID",
                        "Las opciones deben ser Verdadero y Falso.");
            }
        }
    }

    private boolean hasValue(String value) {
        return value != null && !value.isBlank();
    }

    private String contentKey(String text, String mediaId) {
        return (hasValue(text) ? normalize(text) : "") + "|"
                + (hasValue(mediaId) ? mediaId.trim().toLowerCase(Locale.ROOT) : "");
    }

    private String normalize(String value) {
        return Normalizer.normalize(value.trim(), Normalizer.Form.NFKC)
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    private BusinessException error(String code, String message) {
        return new BusinessException(code, message);
    }
}
