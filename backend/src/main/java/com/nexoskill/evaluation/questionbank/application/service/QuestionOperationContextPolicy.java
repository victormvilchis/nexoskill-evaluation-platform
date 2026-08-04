package com.nexoskill.evaluation.questionbank.application.service;

import com.nexoskill.evaluation.organizations.domain.model.ContentScope;
import com.nexoskill.evaluation.organizations.domain.model.TenantContext;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Resuelve permisos operativos de Preguntas sin depender del selector visible,
 * query parameters ni un organizationId enviado libremente por el frontend.
 */
@Component
public class QuestionOperationContextPolicy {

	public OperationContext resolve(TenantContext tenant, ContentScope scope, Long ownerOrganizationId) {
		if (tenant == null || scope == null) {
			throw new BusinessException("QUESTION_CONTEXT_NOT_RESOLVED",
					"No fue posible determinar el contexto autorizado para la operación sobre la pregunta.");
		}
		boolean ownerOrganization = scope == ContentScope.ORGANIZATION && tenant.hasOrganization()
				&& Objects.equals(ownerOrganizationId, tenant.organizationId());
		return new OperationContext(tenant, scope, ownerOrganizationId, ownerOrganization);
	}

	public void assertCanManage(TenantContext tenant, ContentScope scope, Long ownerOrganizationId) {
		OperationContext context = resolve(tenant, scope, ownerOrganizationId);
		if (context.tenant().globalAdministrator()) {
			return;
		}
		if (context.scope() == ContentScope.GLOBAL) {
			throw new BusinessException("QUESTION_GLOBAL_MANAGEMENT_FORBIDDEN",
					"Solo el Administrador global puede modificar una pregunta GLOBAL.");
		}
		if (!context.ownerOrganization()) {
			throw new BusinessException("QUESTION_OPERATION_FORBIDDEN",
					"No tienes permisos para modificar una pregunta de otra organización.");
		}
	}

	public record OperationContext(TenantContext tenant, ContentScope scope, Long ownerOrganizationId,
			boolean ownerOrganization) {
	}
}
