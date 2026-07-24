package com.nexoskill.evaluation.authentication.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EmailNormalizerTest {

    @Test
    void shouldTrimAndUppercaseEmail() {
        assertThat(EmailNormalizer.normalize("  Admin@NexoSkill.local "))
                .isEqualTo("ADMIN@NEXOSKILL.LOCAL");
    }

    @Test
    void shouldReturnEmptyForNull() {
        assertThat(EmailNormalizer.normalize(null)).isEmpty();
    }
}
