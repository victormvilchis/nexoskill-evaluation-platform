package com.nexoskill.evaluation.questionbank.interfaces.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdateQuestionRequest(
        @NotBlank String typeCode,
        @NotBlank String difficultyCode,
        @NotBlank String categoryPublicId,
        @NotBlank @Size(max = 10000) String statement,
        @Size(max = 10000) String explanation,
        @Size(max = 500) String changeSummary,
        @NotEmpty List<@Valid CreateQuestionOptionRequest> options,
        @NotNull @PositiveOrZero Long expectedEntityVersion
) {
}
