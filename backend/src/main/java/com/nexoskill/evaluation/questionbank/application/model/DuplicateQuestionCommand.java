package com.nexoskill.evaluation.questionbank.application.model;

public record DuplicateQuestionCommand(
        String publicId,
        Long actorUserId,
        String ipAddress,
        String userAgent
) {
}
