package com.nexoskill.evaluation.questionbank.application.model;

import com.nexoskill.evaluation.questionbank.domain.model.QuestionStatus;
import java.time.Instant;
import java.util.List;

public record QuestionSummary(
        String publicId,
        String statement,
        String typeCode,
        String typeName,
        List<QuestionCategoryRef> categories,
        QuestionStatus status,
        boolean hasMedia,
        boolean hasCode,
        List<QuestionUsageRef> forms,
        List<QuestionUsageRef> collections,
        Instant createdAt,
        Instant updatedAt) {
    public QuestionSummary {
        categories = List.copyOf(categories);
        forms = List.copyOf(forms);
        collections = List.copyOf(collections);
    }
}
