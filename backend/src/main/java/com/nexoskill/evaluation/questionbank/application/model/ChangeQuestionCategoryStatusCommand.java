package com.nexoskill.evaluation.questionbank.application.model;

import com.nexoskill.evaluation.questionbank.domain.model.CatalogStatus;

public record ChangeQuestionCategoryStatusCommand(
        String publicId,
        CatalogStatus targetStatus,
        Long actorUserId,
        String ipAddress,
        String userAgent
) {
}
