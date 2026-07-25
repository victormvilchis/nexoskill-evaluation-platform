package com.nexoskill.evaluation.questionbank.interfaces.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateQuestionCategoryRequest(
        @Size(max = 80, message = "El código no puede superar 80 caracteres.")
        String code,

        @NotBlank(message = "El nombre de la categoría es obligatorio.")
        @Size(max = 150, message = "El nombre no puede superar 150 caracteres.")
        String name,

        @Size(max = 500, message = "La descripción no puede superar 500 caracteres.")
        String description
) {
}
