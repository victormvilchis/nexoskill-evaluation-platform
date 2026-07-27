package com.nexoskill.evaluation.questionbank.application.model;

import com.nexoskill.evaluation.questionbank.domain.model.QuestionStatus;
import java.time.Instant;
import java.util.List;

public record QuestionSummary(
        String publicId,
        String statement,
        String typeCode,
        String typeName,
        String difficultyCode,
        String difficultyName,
        String levelCode,
        QuestionTechnologySummary technology,
        List<QuestionCategoryRef> categories,
        QuestionStatus status,
        boolean hasMedia,
        boolean hasCode,
        boolean inUse,
        QuestionOwnershipView ownership,
        List<QuestionUsageRef> forms,
        List<QuestionUsageRef> collections,
        long entityVersion,
        Instant createdAt,
        Instant updatedAt) {
    public QuestionSummary {
        categories = List.copyOf(categories);
        forms = List.copyOf(forms);
        collections = List.copyOf(collections);
    }
}
