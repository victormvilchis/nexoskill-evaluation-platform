package com.nexoskill.evaluation.questionbank.application.model;

public record QuestionOptionView(
        String publicId,
        int order,
        String text,
        boolean correct
) {
}
