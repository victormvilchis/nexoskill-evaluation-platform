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
        List<QuestionCategoryRef> categories,
        QuestionStatus status,
        long entityVersion,
        QuestionMediaView promptMedia,
        String codeLanguage,
        String codeContent,
        QuestionAnswerSettings answerSettings,
        List<QuestionOptionView> options,
        List<QuestionUsageRef> forms,
        List<QuestionUsageRef> collections,
        Instant createdAt,
        Instant updatedAt) {
    public QuestionDetail {
        categories = List.copyOf(categories);
        options = List.copyOf(options);
        forms = List.copyOf(forms);
        collections = List.copyOf(collections);
    }
}
