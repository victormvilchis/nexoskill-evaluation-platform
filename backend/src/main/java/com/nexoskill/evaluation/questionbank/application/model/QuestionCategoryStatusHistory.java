package com.nexoskill.evaluation.questionbank.application.model;

import com.nexoskill.evaluation.questionbank.domain.model.CatalogStatus;
import java.time.Instant;

public record QuestionCategoryStatusHistory(
        Long id,
        CatalogStatus previousStatus,
        CatalogStatus newStatus,
        String reason,
        Long actorUserId,
        Instant occurredAt) {
}
