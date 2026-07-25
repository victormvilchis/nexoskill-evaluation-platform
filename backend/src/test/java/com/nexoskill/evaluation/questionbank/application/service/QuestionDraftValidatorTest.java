package com.nexoskill.evaluation.questionbank.application.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexoskill.evaluation.questionbank.application.model.QuestionOptionCommand;
import com.nexoskill.evaluation.questionbank.domain.model.QuestionTypeCode;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import java.util.List;
import org.junit.jupiter.api.Test;

class QuestionDraftValidatorTest {

    private final QuestionDraftValidator validator = new QuestionDraftValidator();

    @Test
    void acceptsSingleChoiceWithOneCorrectOption() {
        assertThatCode(() -> validator.validate(
                QuestionTypeCode.SINGLE_CHOICE,
                "¿Cuál opción es correcta?",
                List.of(
                        new QuestionOptionCommand("Opción A", true),
                        new QuestionOptionCommand("Opción B", false)
                )
        )).doesNotThrowAnyException();
    }

    @Test
    void rejectsSingleChoiceWithTwoCorrectOptions() {
        assertThatThrownBy(() -> validator.validate(
                QuestionTypeCode.SINGLE_CHOICE,
                "Pregunta",
                List.of(
                        new QuestionOptionCommand("A", true),
                        new QuestionOptionCommand("B", true)
                )
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("QUESTION_SINGLE_CORRECT_REQUIRED");
    }

    @Test
    void rejectsDuplicatedOptions() {
        assertThatThrownBy(() -> validator.validate(
                QuestionTypeCode.MULTIPLE_CHOICE,
                "Pregunta",
                List.of(
                        new QuestionOptionCommand("Respuesta", true),
                        new QuestionOptionCommand(" respuesta ", true),
                        new QuestionOptionCommand("Otra", false)
                )
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("QUESTION_OPTION_DUPLICATED");
    }

    @Test
    void validatesTrueFalseLabels() {
        assertThatCode(() -> validator.validate(
                QuestionTypeCode.TRUE_FALSE,
                "Java es un lenguaje orientado a objetos.",
                List.of(
                        new QuestionOptionCommand("Verdadero", true),
                        new QuestionOptionCommand("Falso", false)
                )
        )).doesNotThrowAnyException();
    }
}
