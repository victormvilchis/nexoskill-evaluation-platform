package com.nexoskill.evaluation.questionbank.application.model;

import com.nexoskill.evaluation.organizations.domain.model.ContentScope;

public record QuestionOwnershipView(ContentScope scope, String organizationPublicId, String organizationCode,
		String organizationName, String creatorPublicId, String creatorName, String sourceOrganizationPublicId,
		String sourceOrganizationName, String sourceQuestionPublicId, Long sourceQuestionVersion,
		boolean clonedToGlobal) {
}
