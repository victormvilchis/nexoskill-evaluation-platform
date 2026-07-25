package com.nexoskill.evaluation.questionbank.application.model;

import java.util.List;

public record QuestionHistory(
        String questionPublicId,
        List<QuestionVersionSummary> versions
) {
    public QuestionHistory {
        versions = List.copyOf(versions);
    }
}
