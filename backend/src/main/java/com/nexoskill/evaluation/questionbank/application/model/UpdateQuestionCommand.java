package com.nexoskill.evaluation.questionbank.application.model;

import java.util.List;

public record UpdateQuestionCommand(
        String publicId,
        String typeCode,
        String difficultyCode,
        String technologyPublicId,
        String levelCode,
        List<String> categoryPublicIds,
        List<String> tags,
        String statement,
        String explanation,
        String promptMediaPublicId,
        String codeContent,
        QuestionAnswerSettings answerSettings,
        List<QuestionOptionCommand> options,
        long expectedEntityVersion,
        Long actorUserId) {
    public UpdateQuestionCommand {
        categoryPublicIds = categoryPublicIds == null ? List.of() : List.copyOf(categoryPublicIds);
        tags = tags == null ? List.of() : List.copyOf(tags);
        options = options == null ? List.of() : List.copyOf(options);
        answerSettings = answerSettings == null ? QuestionAnswerSettings.empty() : answerSettings;
    }

    public UpdateQuestionCommand(String publicId, String typeCode, String difficultyCode,
            String technologyPublicId, String levelCode, List<String> categoryPublicIds,
            String statement, String explanation, String promptMediaPublicId, String codeContent,
            QuestionAnswerSettings answerSettings, List<QuestionOptionCommand> options,
            long expectedEntityVersion, Long actorUserId) {
        this(publicId, typeCode, difficultyCode, technologyPublicId, levelCode,
                categoryPublicIds, List.of(), statement, explanation, promptMediaPublicId,
                codeContent, answerSettings, options, expectedEntityVersion, actorUserId);
    }

    public UpdateQuestionCommand(String publicId, String typeCode, List<String> categoryPublicIds,
            String statement, String explanation, String promptMediaPublicId, String codeContent,
            QuestionAnswerSettings answerSettings, List<QuestionOptionCommand> options,
            long expectedEntityVersion, Long actorUserId) {
        this(publicId, typeCode, null, null, null, categoryPublicIds, List.of(), statement, explanation,
                promptMediaPublicId, codeContent, answerSettings, options, expectedEntityVersion, actorUserId);
    }
}
