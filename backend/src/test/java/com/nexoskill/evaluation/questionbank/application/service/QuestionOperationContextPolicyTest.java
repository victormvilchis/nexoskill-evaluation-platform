package com.nexoskill.evaluation.questionbank.application.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexoskill.evaluation.organizations.domain.model.ContentScope;
import com.nexoskill.evaluation.organizations.domain.model.TenantContext;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import org.junit.jupiter.api.Test;

class QuestionOperationContextPolicyTest {
	private final QuestionOperationContextPolicy policy = new QuestionOperationContextPolicy();

	@Test
	void globalAdministratorCanManageGlobalQuestionFromOrganizationalContext() {
		assertThatCode(() -> policy.assertCanManage(TenantContext.organization(21L, "org-21", "ORG_21", true),
				ContentScope.GLOBAL, 1L)).doesNotThrowAnyException();
	}

	@Test
	void globalAdministratorCanManageOrganizationalQuestionFromGlobalContext() {
		assertThatCode(() -> policy.assertCanManage(TenantContext.global(1L, "global", "GLOBAL"),
				ContentScope.ORGANIZATION, 21L)).doesNotThrowAnyException();
	}

	@Test
	void managerCanManageQuestionOwnedByItsOrganization() {
		assertThatCode(() -> policy.assertCanManage(TenantContext.organization(21L, "org-21", "ORG_21", false),
				ContentScope.ORGANIZATION, 21L)).doesNotThrowAnyException();
	}

	@Test
	void managerCannotManageGlobalOrCrossOrganizationQuestion() {
		TenantContext tenant = TenantContext.organization(21L, "org-21", "ORG_21", false);
		assertThatThrownBy(() -> policy.assertCanManage(tenant, ContentScope.GLOBAL, 1L))
				.isInstanceOf(BusinessException.class);
		assertThatThrownBy(() -> policy.assertCanManage(tenant, ContentScope.ORGANIZATION, 22L))
				.isInstanceOf(BusinessException.class);
	}
}
