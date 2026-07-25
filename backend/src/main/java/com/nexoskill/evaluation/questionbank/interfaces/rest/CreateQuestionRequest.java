package com.nexoskill.evaluation.questionbank.interfaces.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateQuestionRequest(
        @NotBlank(message = "El tipo de pregunta es obligatorio.")
        String typeCode,

        @NotBlank(message = "La dificultad es obligatoria.")
        String difficultyCode,

        @NotBlank(message = "La categoría es obligatoria.")
        String categoryPublicId,

        @NotBlank(message = "El enunciado es obligatorio.")
        @Size(max = 10000, message = "El enunciado no puede superar 10,000 caracteres.")
        String statement,

        @Size(max = 10000, message = "La explicación no puede superar 10,000 caracteres.")
        String explanation,

        @NotEmpty(message = "La pregunta debe incluir opciones.")
        @Size(min = 2, max = 10, message = "La pregunta debe tener entre 2 y 10 opciones.")
        List<@Valid CreateQuestionOptionRequest> options
) {
}
