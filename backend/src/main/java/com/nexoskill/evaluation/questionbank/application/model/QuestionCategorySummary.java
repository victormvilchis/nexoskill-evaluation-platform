package com.nexoskill.evaluation.questionbank.application.model;

import com.nexoskill.evaluation.organizations.domain.model.ContentScope;
import com.nexoskill.evaluation.questionbank.domain.model.CatalogStatus;
import java.time.Instant;

public record QuestionCategorySummary(
        String publicId,
        String code,
        String name,
        String description,
        CatalogStatus status,
        ContentScope contentScope,
        String ownerOrganizationPublicId,
        String ownerOrganizationName,
        long entityVersion,
        long questionCount,
        Long createdBy,
        Instant createdAt,
        Instant updatedAt) {
}
