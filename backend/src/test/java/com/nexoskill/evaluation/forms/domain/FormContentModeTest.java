package com.nexoskill.evaluation.forms.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexoskill.evaluation.shared.domain.BusinessException;
import org.junit.jupiter.api.Test;

class FormContentModeTest {
    @Test
    void defaultsToManualForLegacyCommands() {
        assertThat(FormContentMode.parse(null)).isEqualTo(FormContentMode.MANUAL);
        assertThat(FormContentMode.parse(" ")).isEqualTo(FormContentMode.MANUAL);
    }

    @Test
    void acceptsRandomPoolCaseInsensitively() {
        assertThat(FormContentMode.parse("random_pool")).isEqualTo(FormContentMode.RANDOM_POOL);
    }

    @Test
    void rejectsUnknownModes() {
        assertThatThrownBy(() -> FormContentMode.parse("SECTIONS"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("modalidad de contenido");
    }
}
