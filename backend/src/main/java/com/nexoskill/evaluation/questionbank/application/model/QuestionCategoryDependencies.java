package com.nexoskill.evaluation.questionbank.application.model;

public record QuestionCategoryDependencies(
        String publicId,
        long questionCount,
        boolean canDelete,
        String message) {
}
