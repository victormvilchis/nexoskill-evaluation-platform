package com.nexoskill.evaluation.questionbank.interfaces.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import com.nexoskill.evaluation.questionbank.domain.model.QuestionAvailabilityMode;

public final class QuestionRequests {
    private QuestionRequests() {
    }

    public record Option(
            String text,
            String mediaPublicId,
            String matchText,
            String matchMediaPublicId,
            boolean correct,
            @Size(max = 4000) String feedback) {
    }

    public record AnswerSettings(
            List<String> acceptedAnswers,
            boolean caseSensitive,
            boolean manualReview,
            BigDecimal numericMin,
            BigDecimal numericMax,
            BigDecimal numericTolerance,
            Integer maxLength) {
    }

    public record Create(
            @NotBlank String typeCode,
            String difficultyCode,
            String technologyPublicId,
            String levelCode,
            @NotEmpty List<String> categoryPublicIds,
            @Size(max = 10, message = "Puedes agregar un máximo de 10 etiquetas.")
            List<@NotBlank @Size(max = 40, message = "Cada etiqueta puede tener hasta 40 caracteres.") String> tags,
            @NotBlank @Size(max = 10000) String statement,
            @Size(max = 10000) String explanation,
            String promptMediaPublicId,
            @Size(max = 30000) String codeContent,
            @Valid AnswerSettings answerSettings,
            List<@Valid Option> options,
            String contentScope,
            String organizationPublicId,
            QuestionAvailabilityMode availabilityMode,
            List<String> availabilityOrganizationPublicIds) {
        public Create(String typeCode, String difficultyCode, String technologyPublicId,
                String levelCode, List<String> categoryPublicIds, String statement,
                String explanation, String promptMediaPublicId, String codeContent,
                AnswerSettings answerSettings, List<Option> options) {
            this(typeCode, difficultyCode, technologyPublicId, levelCode,
                    categoryPublicIds, List.of(), statement, explanation,
                    promptMediaPublicId, codeContent, answerSettings, options, null, null, null, List.of());
        }
    }

    public record Update(
            @NotBlank String typeCode,
            String difficultyCode,
            String technologyPublicId,
            String levelCode,
            @NotEmpty List<String> categoryPublicIds,
            @Size(max = 10, message = "Puedes agregar un máximo de 10 etiquetas.")
            List<@NotBlank @Size(max = 40, message = "Cada etiqueta puede tener hasta 40 caracteres.") String> tags,
            @NotBlank @Size(max = 10000) String statement,
            @Size(max = 10000) String explanation,
            String promptMediaPublicId,
            @Size(max = 30000) String codeContent,
            @Valid AnswerSettings answerSettings,
            List<@Valid Option> options,
            @PositiveOrZero long expectedEntityVersion,
            QuestionAvailabilityMode availabilityMode,
            List<String> availabilityOrganizationPublicIds) {
        public Update(String typeCode, String difficultyCode, String technologyPublicId,
                String levelCode, List<String> categoryPublicIds, String statement,
                String explanation, String promptMediaPublicId, String codeContent,
                AnswerSettings answerSettings, List<Option> options,
                long expectedEntityVersion) {
            this(typeCode, difficultyCode, technologyPublicId, levelCode,
                    categoryPublicIds, List.of(), statement, explanation,
                    promptMediaPublicId, codeContent, answerSettings, options,
                    expectedEntityVersion, null, List.of());
        }
    }

    public record ChangeStatus(@NotBlank String status, @PositiveOrZero long expectedEntityVersion) {
    }

    public record Delete(@Size(max = 500) String reason, @PositiveOrZero long expectedEntityVersion) {
    }

    public record CreateCategory(String code, @NotBlank @Size(max = 150) String name,
            @Size(max = 500) String description) {
    }

    public record UpdateCategory(String code, @NotBlank @Size(max = 150) String name,
            @Size(max = 500) String description, @PositiveOrZero long expectedEntityVersion) {
    }

    public record Status(@PositiveOrZero long expectedEntityVersion) {
    }

    public record Duplicate(String targetScope, String organizationPublicId) {
    }

    public record CloneToGlobal(boolean includeDependencies, @Size(max = 1000) String notes) {
    }

    public record CategoryStatus(@PositiveOrZero long expectedEntityVersion, @Size(max = 500) String reason) {
    }
}
