package com.nexoskill.evaluation.questionbank.application.service;

import com.nexoskill.evaluation.organizations.domain.model.ContentScope;
import com.nexoskill.evaluation.organizations.domain.model.TenantContext;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationJpaEntity;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationRepository;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Resolves the owner of a new question without trusting a free organization id.
 */
@Component
public class QuestionCreationTargetResolver {
	private final OrganizationRepository organizations;
	private final Clock clock;

	public QuestionCreationTargetResolver(OrganizationRepository organizations, Clock clock) {
		this.organizations = organizations;
		this.clock = clock;
	}

	public Target resolve(TenantContext tenant, String requestedScope, String organizationPublicId) {
		if (tenant == null || !tenant.hasOrganization()) {
			throw new BusinessException("QUESTION_CONTEXT_NOT_RESOLVED",
					"No fue posible determinar el contexto autorizado para crear la pregunta.");
		}
		if (!tenant.globalAdministrator()) {
			if ((requestedScope != null && !requestedScope.isBlank()
					&& !"ORGANIZATION".equalsIgnoreCase(requestedScope.trim()))
					|| (organizationPublicId != null && !organizationPublicId.isBlank()
							&& !organizationPublicId.trim().equals(tenant.organizationPublicId()))) {
				throw new BusinessException("QUESTION_OWNER_FORBIDDEN",
						"No tienes permisos para cambiar la organización propietaria de la pregunta.");
			}
			return new Target(ContentScope.ORGANIZATION, tenant.organizationId(), tenant.organizationPublicId());
		}

		ContentScope scope = parseScope(requestedScope, tenant);
		if (scope == ContentScope.GLOBAL) {
			OrganizationJpaEntity global = organizations.findByCode(OrganizationJpaEntity.GLOBAL_CODE)
					.orElseThrow(() -> new BusinessException("GLOBAL_ORGANIZATION_NOT_FOUND",
							"No fue posible resolver la organización GLOBAL."));
			return new Target(ContentScope.GLOBAL, global.getId(), global.getPublicId());
		}

		if (organizationPublicId == null || organizationPublicId.isBlank()) {
			throw new BusinessException("QUESTION_OWNER_ORGANIZATION_REQUIRED",
					"Selecciona la organización propietaria de la pregunta.");
		}
		OrganizationJpaEntity organization = organizations.findByPublicId(organizationPublicId.trim())
				.orElseThrow(() -> new BusinessException("QUESTION_OWNER_ORGANIZATION_INVALID",
						"La organización seleccionada no existe."));
		if (organization.isGlobal() || !organization.isOperational(LocalDate.now(clock))) {
			throw new BusinessException("QUESTION_OWNER_ORGANIZATION_INVALID",
					"La organización propietaria debe ser comercial, activa y vigente.");
		}
		return new Target(ContentScope.ORGANIZATION, organization.getId(), organization.getPublicId());
	}

	private ContentScope parseScope(String value, TenantContext tenant) {
		if (value == null || value.isBlank()) {
			return tenant.globalScope() ? ContentScope.GLOBAL : ContentScope.ORGANIZATION;
		}
		try {
			return ContentScope.valueOf(value.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException exception) {
			throw new BusinessException("QUESTION_SCOPE_INVALID", "El alcance indicado no es válido.");
		}
	}

	public record Target(ContentScope scope, Long organizationId, String organizationPublicId) {
	}
}
