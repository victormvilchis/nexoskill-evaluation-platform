package com.nexoskill.evaluation.questionbank.application.model;

import com.nexoskill.evaluation.questionbank.domain.model.QuestionStatus;
import java.time.Instant;

public record QuestionVersionSummary(
        int versionNumber,
        QuestionStatus status,
        String statement,
        String changeSummary,
        Instant createdAt,
        Instant statusChangedAt,
        Instant publishedAt
) {
}
