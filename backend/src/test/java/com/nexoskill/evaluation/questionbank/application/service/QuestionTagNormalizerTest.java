package com.nexoskill.evaluation.questionbank.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexoskill.evaluation.shared.domain.BusinessException;
import java.util.List;
import org.junit.jupiter.api.Test;

class QuestionTagNormalizerTest {
    @Test
    void shouldNormalizeHashCaseSpacesAndAccents() {
        var value = QuestionTagNormalizer.normalize("  #Cláses   Abstractas  ");

        assertThat(value.displayName()).isEqualTo("Cláses Abstractas");
        assertThat(value.normalizedName()).isEqualTo("clases abstractas");
        assertThat(value.slug()).isEqualTo("clases-abstractas");
    }

    @Test
    void shouldRemoveDuplicatesInsideTheSameQuestion() {
        var values = QuestionTagNormalizer.normalizeAll(List.of(
                "Polimorfismo", "#polimorfismo", " POLIMORFISMO "));

        assertThat(values).hasSize(1);
        assertThat(values.getFirst().slug()).isEqualTo("polimorfismo");
    }

    @Test
    void shouldAllowQuestionsWithoutTags() {
        assertThat(QuestionTagNormalizer.normalizeAll(List.of())).isEmpty();
    }

    @Test
    void shouldRejectMoreThanTenTags() {
        assertThatThrownBy(() -> QuestionTagNormalizer.normalizeAll(List.of(
                "uno", "dos", "tres", "cuatro", "cinco", "seis",
                "siete", "ocho", "nueve", "diez", "once")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("máximo de 10");
    }

    @Test
    void shouldRejectTagsLongerThanFortyCharacters() {
        assertThatThrownBy(() -> QuestionTagNormalizer.normalize("a".repeat(41)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("40 caracteres");
    }

    @Test
    void shouldRejectEmptyAndInvalidTags() {
        assertThatThrownBy(() -> QuestionTagNormalizer.normalize("###"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> QuestionTagNormalizer.normalize("spring@boot"))
                .isInstanceOf(BusinessException.class);
    }
}
