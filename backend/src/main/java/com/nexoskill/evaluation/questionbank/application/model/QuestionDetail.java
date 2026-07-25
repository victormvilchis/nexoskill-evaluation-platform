package com.nexoskill.evaluation.questionbank.application.model;

import com.nexoskill.evaluation.questionbank.domain.model.QuestionStatus;
import java.time.Instant;
import java.util.List;

public record QuestionDetail(
        String publicId,
        String statement,
        String explanation,
        String typeCode,
        String typeName,
        String difficultyCode,
        String difficultyName,
        String categoryPublicId,
        String categoryName,
        QuestionStatus status,
        int versionNumber,
        List<QuestionOptionView> options,
        Instant createdAt,
        Instant updatedAt
) {
    public QuestionDetail {
        options = List.copyOf(options);
    }
}
