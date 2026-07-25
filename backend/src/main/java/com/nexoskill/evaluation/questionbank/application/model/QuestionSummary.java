package com.nexoskill.evaluation.questionbank.application.model;

import com.nexoskill.evaluation.questionbank.domain.model.QuestionStatus;
import java.time.Instant;

public record QuestionSummary(
        String publicId,
        String statement,
        String typeCode,
        String typeName,
        String difficultyCode,
        String difficultyName,
        String categoryPublicId,
        String categoryName,
        QuestionStatus status,
        int versionNumber,
        Instant createdAt,
        Instant updatedAt
) {
}
