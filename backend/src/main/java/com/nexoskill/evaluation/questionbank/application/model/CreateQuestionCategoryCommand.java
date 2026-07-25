package com.nexoskill.evaluation.questionbank.application.model;

public record CreateQuestionCategoryCommand(
        String code,
        String name,
        String description,
        Long actorUserId,
        String ipAddress,
        String userAgent
) {
}
