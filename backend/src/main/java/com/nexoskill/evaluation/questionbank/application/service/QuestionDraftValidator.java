package com.nexoskill.evaluation.questionbank.application.service;

import com.nexoskill.evaluation.questionbank.application.model.QuestionOptionCommand;
import com.nexoskill.evaluation.questionbank.domain.model.QuestionTypeCode;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class QuestionDraftValidator {

    public void validate(
            QuestionTypeCode type,
            String statement,
            List<QuestionOptionCommand> options) {

        if (statement == null || statement.isBlank()) {
            throw new BusinessException(
                    "QUESTION_STATEMENT_REQUIRED",
                    "El enunciado de la pregunta es obligatorio."
            );
        }
        if (statement.trim().length() > 10000) {
            throw new BusinessException(
                    "QUESTION_STATEMENT_TOO_LONG",
                    "El enunciado no puede superar 10,000 caracteres."
            );
        }
        if (options == null || options.size() < 2 || options.size() > 10) {
            throw new BusinessException(
                    "QUESTION_OPTIONS_INVALID",
                    "La pregunta debe contener entre 2 y 10 opciones."
            );
        }

        validateOptionTexts(options);
        long correctOptions = options.stream().filter(QuestionOptionCommand::correct).count();

        switch (type) {
            case SINGLE_CHOICE -> validateSingleChoice(correctOptions);
            case MULTIPLE_CHOICE -> validateMultipleChoice(correctOptions, options.size());
            case TRUE_FALSE -> validateTrueFalse(correctOptions, options);
        }
    }

    public void validateForPublication(
            QuestionTypeCode type,
            String statement,
            String explanation,
            List<QuestionOptionCommand> options) {
        validate(type, statement, options);
        if (explanation == null || explanation.isBlank()) {
            throw new BusinessException(
                    "QUESTION_EXPLANATION_REQUIRED_FOR_PUBLICATION",
                    "Agrega una explicación antes de publicar la pregunta."
            );
        }
    }

    private void validateOptionTexts(List<QuestionOptionCommand> options) {
        Set<String> normalizedTexts = new HashSet<>();
        for (QuestionOptionCommand option : options) {
            if (option.text() == null || option.text().isBlank()) {
                throw new BusinessException(
                        "QUESTION_OPTION_TEXT_REQUIRED",
                        "Todas las opciones deben tener contenido."
                );
            }
            if (option.text().trim().length() > 2000) {
                throw new BusinessException(
                        "QUESTION_OPTION_TOO_LONG",
                        "Una opción no puede superar 2,000 caracteres."
                );
            }
            String normalized = option.text().trim().toLowerCase(Locale.ROOT);
            if (!normalizedTexts.add(normalized)) {
                throw new BusinessException(
                        "QUESTION_OPTION_DUPLICATED",
                        "No puede haber opciones duplicadas."
                );
            }
        }
    }

    private void validateSingleChoice(long correctOptions) {
        if (correctOptions != 1) {
            throw new BusinessException(
                    "QUESTION_SINGLE_CORRECT_REQUIRED",
                    "Una pregunta de opción única debe tener exactamente una respuesta correcta."
            );
        }
    }

    private void validateMultipleChoice(long correctOptions, int optionCount) {
        if (optionCount < 3 || correctOptions < 2 || correctOptions >= optionCount) {
            throw new BusinessException(
                    "QUESTION_MULTIPLE_CORRECT_INVALID",
                    "Una pregunta de opción múltiple debe tener al menos tres opciones, dos respuestas correctas y una incorrecta."
            );
        }
    }

    private void validateTrueFalse(
            long correctOptions,
            List<QuestionOptionCommand> options) {
        if (options.size() != 2 || correctOptions != 1) {
            throw new BusinessException(
                    "QUESTION_TRUE_FALSE_INVALID",
                    "Una pregunta de verdadero o falso debe tener dos opciones y una respuesta correcta."
            );
        }
        Set<String> texts = options.stream()
                .map(QuestionOptionCommand::text)
                .map(String::trim)
                .map(value -> value.toUpperCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        if (!texts.equals(Set.of("VERDADERO", "FALSO"))) {
            throw new BusinessException(
                    "QUESTION_TRUE_FALSE_OPTIONS_INVALID",
                    "Las opciones deben ser Verdadero y Falso."
            );
        }
    }
}
