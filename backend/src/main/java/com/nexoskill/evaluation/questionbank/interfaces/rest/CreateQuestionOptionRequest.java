package com.nexoskill.evaluation.questionbank.interfaces.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateQuestionOptionRequest(
        @NotBlank(message = "El texto de la opción es obligatorio.")
        @Size(max = 2000, message = "La opción no puede superar 2,000 caracteres.")
        String text,
        boolean correct
) {
}
