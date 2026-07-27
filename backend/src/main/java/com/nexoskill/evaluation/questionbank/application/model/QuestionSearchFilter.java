package com.nexoskill.evaluation.questionbank.application.model;

import com.nexoskill.evaluation.organizations.domain.model.ContentScope;
import com.nexoskill.evaluation.questionbank.domain.model.QuestionStatus;
import java.time.LocalDate;

public record QuestionSearchFilter(
        String query,
        QuestionStatus status,
        String typeCode,
        String categoryPublicId,
        ContentScope scope,
        String organizationPublicId,
        String technologyPublicId,
        String difficultyCode,
        String levelCode,
        String creatorPublicId,
        LocalDate createdFrom,
        LocalDate createdTo,
        LocalDate updatedFrom,
        LocalDate updatedTo,
        Boolean clonedToGlobal,
        Boolean inUse) {
}
