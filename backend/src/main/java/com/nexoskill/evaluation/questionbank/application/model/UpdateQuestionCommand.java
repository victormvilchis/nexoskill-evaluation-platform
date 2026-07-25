package com.nexoskill.evaluation.questionbank.application.model;

import java.util.List;

public record UpdateQuestionCommand(
        String publicId,
        String typeCode,
        String difficultyCode,
        String categoryPublicId,
        String statement,
        String explanation,
        String changeSummary,
        List<QuestionOptionCommand> options,
        long expectedEntityVersion,
        Long actorUserId,
        String ipAddress,
        String userAgent
) {
    public UpdateQuestionCommand {
        options = options == null ? List.of() : List.copyOf(options);
    }
}
