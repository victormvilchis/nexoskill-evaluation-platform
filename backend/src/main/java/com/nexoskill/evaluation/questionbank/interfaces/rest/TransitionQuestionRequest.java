package com.nexoskill.evaluation.questionbank.interfaces.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record TransitionQuestionRequest(
        @NotBlank String targetStatus,
        @NotNull @PositiveOrZero Long expectedEntityVersion
) {
}
