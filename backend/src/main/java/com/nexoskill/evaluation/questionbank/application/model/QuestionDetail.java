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
        String levelCode,
        QuestionTechnologySummary technology,
        List<QuestionCategoryRef> categories,
        List<QuestionTagView> tags,
        QuestionStatus status,
        long entityVersion,
        QuestionMediaView promptMedia,
        String codeLanguage,
        String codeContent,
        QuestionAnswerSettings answerSettings,
        List<QuestionOptionView> options,
        QuestionOwnershipView ownership,
        List<QuestionUsageRef> forms,
        List<QuestionUsageRef> collections,
        Instant createdAt,
        Instant updatedAt) {
    public QuestionDetail {
        categories = List.copyOf(categories);
        tags = tags == null ? List.of() : List.copyOf(tags);
        options = List.copyOf(options);
        forms = List.copyOf(forms);
        collections = List.copyOf(collections);
    }


    public QuestionDetail(
            String publicId, String statement, String explanation,
            String typeCode, String typeName, String difficultyCode,
            String difficultyName, String levelCode, QuestionTechnologySummary technology,
            List<QuestionCategoryRef> categories, QuestionStatus status,
            long entityVersion, QuestionMediaView promptMedia, String codeLanguage,
            String codeContent, QuestionAnswerSettings answerSettings,
            List<QuestionOptionView> options, QuestionOwnershipView ownership,
            List<QuestionUsageRef> forms, List<QuestionUsageRef> collections,
            Instant createdAt, Instant updatedAt) {
        this(publicId, statement, explanation, typeCode, typeName, difficultyCode,
                difficultyName, levelCode, technology, categories, List.of(), status,
                entityVersion, promptMedia, codeLanguage, codeContent, answerSettings,
                options, ownership, forms, collections, createdAt, updatedAt);
    }
}
