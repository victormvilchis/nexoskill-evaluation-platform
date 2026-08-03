package com.nexoskill.evaluation.forms.application;

import com.nexoskill.evaluation.organizations.domain.model.ContentScope;
import com.nexoskill.evaluation.organizations.domain.model.TenantContext;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationJpaEntity;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationRepository;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class FormCreationTargetResolver {
    private final OrganizationRepository organizations;
    private final Clock clock;

    public FormCreationTargetResolver(OrganizationRepository organizations, Clock clock) {
        this.organizations = organizations;
        this.clock = clock;
    }

    public Target resolve(TenantContext tenant, String requestedScope, String organizationPublicId) {
        if (tenant == null || !tenant.hasOrganization()) {
            throw new BusinessException("FORM_CONTEXT_NOT_RESOLVED",
                    "No fue posible determinar el contexto autorizado para crear el formulario.");
        }
        if (!tenant.globalAdministrator()) {
            if ((requestedScope != null && !requestedScope.isBlank()
                    && !ContentScope.ORGANIZATION.name().equalsIgnoreCase(requestedScope.trim()))
                    || (organizationPublicId != null && !organizationPublicId.isBlank()
                    && !organizationPublicId.trim().equals(tenant.organizationPublicId()))) {
                throw new BusinessException("FORM_OWNER_FORBIDDEN",
                        "No tienes permisos para cambiar la organización propietaria del formulario.");
            }
            return new Target(ContentScope.ORGANIZATION, tenant.organizationId(),
                    tenant.organizationPublicId(), tenant.organizationCode(), tenant.organizationCode());
        }

        ContentScope scope = parseScope(requestedScope, tenant);
        if (scope == ContentScope.GLOBAL) {
            OrganizationJpaEntity global = organizations.findByCode(OrganizationJpaEntity.GLOBAL_CODE)
                    .orElseThrow(() -> new BusinessException("GLOBAL_ORGANIZATION_NOT_FOUND",
                            "No fue posible resolver la organización GLOBAL."));
            return new Target(ContentScope.GLOBAL, global.getId(), global.getPublicId(),
                    global.getCode(), global.getName());
        }
        if (organizationPublicId == null || organizationPublicId.isBlank()) {
            throw new BusinessException("FORM_OWNER_ORGANIZATION_REQUIRED",
                    "Selecciona la organización propietaria del formulario.");
        }
        OrganizationJpaEntity organization = organizations.findByPublicId(organizationPublicId.trim())
                .orElseThrow(() -> new BusinessException("FORM_OWNER_ORGANIZATION_INVALID",
                        "La organización seleccionada no existe."));
        if (organization.isGlobal() || !organization.isOperational(LocalDate.now(clock))) {
            throw new BusinessException("FORM_OWNER_ORGANIZATION_INVALID",
                    "La organización propietaria debe ser comercial, activa y vigente.");
        }
        return new Target(ContentScope.ORGANIZATION, organization.getId(), organization.getPublicId(),
                organization.getCode(), organization.getName());
    }

    public Target fromExisting(ContentScope scope, Long organizationId) {
        OrganizationJpaEntity organization = organizations.findById(organizationId)
                .orElseThrow(() -> new BusinessException("FORM_OWNER_ORGANIZATION_INVALID",
                        "No fue posible resolver la organización propietaria del formulario."));
        return new Target(scope, organization.getId(), organization.getPublicId(),
                organization.getCode(), organization.getName());
    }

    private ContentScope parseScope(String value, TenantContext tenant) {
        if (value == null || value.isBlank()) {
            return tenant.globalScope() ? ContentScope.GLOBAL : ContentScope.ORGANIZATION;
        }
        try {
            return ContentScope.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("FORM_SCOPE_INVALID", "El alcance indicado no es válido.");
        }
    }

    public record Target(ContentScope scope, Long organizationId, String organizationPublicId,
            String organizationCode, String organizationName) {
        public TenantContext asReadContext() {
            return scope == ContentScope.GLOBAL
                    ? TenantContext.global(organizationId, organizationPublicId, organizationCode)
                    : TenantContext.organization(organizationId, organizationPublicId, organizationCode, true);
        }
    }
}
