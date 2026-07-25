package com.nexoskill.evaluation.questionbank.application.model;

import java.util.List;

public record CreateQuestionCommand(
        String typeCode,
        String difficultyCode,
        String categoryPublicId,
        String statement,
        String explanation,
        List<QuestionOptionCommand> options,
        Long actorUserId,
        String ipAddress,
        String userAgent
) {
    public CreateQuestionCommand {
        options = options == null ? List.of() : List.copyOf(options);
    }
}
