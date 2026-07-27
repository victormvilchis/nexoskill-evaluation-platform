package com.nexoskill.evaluation.globalcontent.application.service;

import com.nexoskill.evaluation.globalcontent.domain.model.DistributionMode;
import com.nexoskill.evaluation.globalcontent.domain.model.EditorialStatus;
import com.nexoskill.evaluation.globalcontent.domain.model.GlobalContentType;
import com.nexoskill.evaluation.globalcontent.domain.model.GrantStatus;
import com.nexoskill.evaluation.globalcontent.infrastructure.persistence.ContentReplicationLinkRepository;
import com.nexoskill.evaluation.globalcontent.infrastructure.persistence.GlobalContentVersionRepository;
import com.nexoskill.evaluation.globalcontent.infrastructure.persistence.OrganizationGlobalContentGrantRepository;
import com.nexoskill.evaluation.organizations.domain.model.ContentMode;
import com.nexoskill.evaluation.organizations.domain.model.ContentScope;
import com.nexoskill.evaluation.organizations.domain.model.TenantContext;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationJpaEntity;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationRepository;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Centraliza las reglas de lectura y edición del contenido GLOBAL y ORGANIZATION.
 * Los módulos operativos no deben inferir acceso únicamente a partir de un ID
 * recibido desde el frontend.
 */
@Component
public class GlobalContentAccessPolicy {
    private final OrganizationRepository organizations;
    private final OrganizationGlobalContentGrantRepository grants;
    private final ContentReplicationLinkRepository replicationLinks;
    private final GlobalContentVersionRepository versions;
    private final Clock clock;

    public GlobalContentAccessPolicy(OrganizationRepository organizations,
            OrganizationGlobalContentGrantRepository grants,
            ContentReplicationLinkRepository replicationLinks,
            GlobalContentVersionRepository versions,
            Clock clock) {
        this.organizations = organizations;
        this.grants = grants;
        this.replicationLinks = replicationLinks;
        this.versions = versions;
        this.clock = clock;
    }

    public boolean canRead(GlobalContentType type, Long internalId, ContentScope scope,
            Long ownerOrganizationId, TenantContext tenant) {
        if (tenant == null || scope == null || internalId == null) return false;

        if (tenant.globalScope()) {
            // El Administrador global opera el Banco de Preguntas como centro transversal.
            // La lectura de contenido organizacional sigue siendo explícita y auditada,
            // pero ya no depende de un módulo paralelo de “Gobierno global”.
            return tenant.globalAdministrator();
        }

        if (!tenant.hasOrganization()) return false;
        if (scope == ContentScope.ORGANIZATION) {
            return Objects.equals(ownerOrganizationId, tenant.organizationId());
        }

        OrganizationJpaEntity organization = organizations.findById(tenant.organizationId()).orElse(null);
        if (organization == null || organization.isGlobal()) return false;
        if (!isPublishedOrLegacy(type, internalId)) return false;
        if (organization.getContentMode() == ContentMode.GLOBAL_CATALOG) return true;

        // CLEAN y CUSTOM no exponen el catálogo maestro completo, pero sí deben
        // respetar habilitaciones explícitas otorgadas por el Administrador global.
        Instant now = clock.instant();
        return grants.findAllByOrganizationIdAndContentTypeAndGlobalContentIdAndStatus(
                        tenant.organizationId(), type, internalId, GrantStatus.ACTIVE)
                .stream()
                .anyMatch(grant -> grant.getDistributionMode() == DistributionMode.GLOBAL_REFERENCE
                        && grant.isOperational(now));
    }

    private boolean isPublishedOrLegacy(GlobalContentType type, Long internalId) {
        if (!versions.existsByContentTypeAndContentId(type, internalId)) return true;
        return versions.findFirstByContentTypeAndContentIdAndStatusOrderByVersionNumberDesc(
                type, internalId, EditorialStatus.PUBLISHED).isPresent();
    }

    public void assertReadable(GlobalContentType type, Long internalId, ContentScope scope,
            Long ownerOrganizationId, TenantContext tenant, String notFoundCode, String notFoundMessage) {
        if (!canRead(type, internalId, scope, ownerOrganizationId, tenant)) {
            // Se responde como recurso inexistente para no revelar contenido privado
            // de otra organización.
            throw new BusinessException(notFoundCode, notFoundMessage);
        }
    }

    public void assertEditable(GlobalContentType type, Long internalId, ContentScope scope,
            Long ownerOrganizationId, Long sourceGlobalId, TenantContext tenant) {
        if (scope == ContentScope.GLOBAL) {
            if (!tenant.globalAdministrator() || !tenant.globalScope()) {
                throw new BusinessException("GLOBAL_CONTENT_IMMUTABLE",
                        "El contenido global solamente puede modificarse desde el contexto GLOBAL.");
            }
            return;
        }
        // La edición transversal de contenido organizacional es exclusiva del
        // Administrador global y se registra desde el adaptador del módulo.
        if (tenant.globalAdministrator() && tenant.globalScope()) return;
        if (!tenant.hasOrganization() || !Objects.equals(ownerOrganizationId, tenant.organizationId())) {
            throw new BusinessException("CONTENT_NOT_FOUND", "El contenido solicitado no existe.");
        }
        if (sourceGlobalId == null) return;

        var link = replicationLinks.findByOrganizationIdAndContentTypeAndTargetContentId(
                tenant.organizationId(), type, internalId).orElse(null);
        if (link == null) {
            throw new BusinessException("GLOBAL_CONTENT_COPY_POLICY_MISSING",
                    "No se encontró la política de edición de la copia organizacional.");
        }
        boolean editable = grants.findAllByOrganizationIdAndContentTypeAndGlobalContentIdAndStatus(
                        tenant.organizationId(), type, sourceGlobalId, GrantStatus.ACTIVE)
                .stream()
                .filter(grant -> grant.isOperational(clock.instant()))
                .anyMatch(grant -> grant.getDistributionMode() == DistributionMode.ORGANIZATION_COPY
                        && grant.isOrganizationEditable());
        if (!editable) {
            throw new BusinessException("GLOBAL_CONTENT_COPY_READ_ONLY",
                    "La copia organizacional está configurada como solo lectura.");
        }
    }

    public boolean allowsAllGlobal(TenantContext tenant) {
        if (tenant == null || tenant.globalScope() || !tenant.hasOrganization()) return false;
        return organizations.findById(tenant.organizationId())
                .map(organization -> organization.getContentMode() == ContentMode.GLOBAL_CATALOG)
                .orElse(false);
    }

    public Ownership ownershipForCreation(TenantContext tenant) {
        if (tenant.globalScope()) {
            if (!tenant.hasOrganization()) {
                throw new BusinessException("GLOBAL_ORGANIZATION_NOT_FOUND",
                        "No fue posible resolver la organización GLOBAL.");
            }
            return new Ownership(ContentScope.GLOBAL, tenant.organizationId());
        }
        if (!tenant.hasOrganization()) {
            throw new BusinessException("TENANT_NOT_RESOLVED",
                    "No fue posible determinar la organización para completar la operación.");
        }
        return new Ownership(ContentScope.ORGANIZATION, tenant.organizationId());
    }

    public record Ownership(ContentScope scope, Long organizationId) {}
}
