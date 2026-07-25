package com.nexoskill.evaluation.questionbank.application.model;

public record QuestionOptionView(
        String publicId,
        int order,
        String text,
        QuestionMediaView media,
        String matchText,
        QuestionMediaView matchMedia,
        boolean correct,
        String feedback) {
}
