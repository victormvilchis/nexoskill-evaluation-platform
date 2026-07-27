package com.nexoskill.evaluation.globalcontent.application.service;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.globalcontent.application.model.GlobalContentModels.*;
import com.nexoskill.evaluation.globalcontent.application.port.out.GlobalContentResourcePort;
import com.nexoskill.evaluation.globalcontent.domain.model.*;
import com.nexoskill.evaluation.globalcontent.infrastructure.persistence.*;
import com.nexoskill.evaluation.organizations.domain.model.ContentScope;
import com.nexoskill.evaluation.organizations.domain.model.OrganizationType;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationJpaEntity;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationRepository;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationContentGrantService {
    private final OrganizationGlobalContentGrantRepository grants;
    private final ContentReplicationLinkRepository links;
    private final GlobalContentResourcePort resources;
    private final GlobalContentVersionService versions;
    private final ContentReplicationService replication;
    private final OrganizationRepository organizations;
    private final AuditLogPort audit;
    private final Clock clock;

    public OrganizationContentGrantService(OrganizationGlobalContentGrantRepository grants,
            ContentReplicationLinkRepository links,
            GlobalContentResourcePort resources,
            GlobalContentVersionService versions,
            ContentReplicationService replication,
            OrganizationRepository organizations,
            AuditLogPort audit,
            Clock clock) {
        this.grants = grants;
        this.links = links;
        this.resources = resources;
        this.versions = versions;
        this.replication = replication;
        this.organizations = organizations;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public GrantView grant(GrantCommand command, Long actorUserId) {
        return grantInternal(command, actorUserId).view();
    }

    @Transactional
    public GrantOutcome grantInternal(GrantCommand command, Long actorUserId) {
        validate(command);
        OrganizationJpaEntity organization = organization(command.organizationPublicId());
        ContentResource global = resources.find(command.contentType(), command.globalContentPublicId());
        if (global.scope() != ContentScope.GLOBAL) {
            throw new BusinessException("GLOBAL_CONTENT_SCOPE_REQUIRED",
                    "La habilitación requiere contenido perteneciente a GLOBAL.");
        }
        if (!versions.isPublished(command.contentType(), global.internalId(), command.globalVersion())) {
            throw new BusinessException("GLOBAL_CONTENT_NOT_PUBLISHED",
                    "Solo el contenido global publicado puede habilitarse.");
        }

        Optional<OrganizationGlobalContentGrantJpaEntity> existing = grants
                .findByOrganizationIdAndContentTypeAndGlobalContentIdAndGlobalVersionAndDistributionMode(
                        organization.getId(), command.contentType(), global.internalId(), command.globalVersion(),
                        command.distributionMode());
        ReplicatedResource target = null;
        boolean skipped = false;
        if (command.distributionMode() == DistributionMode.ORGANIZATION_COPY) {
            target = replication.replicate(command.contentType(), global.internalId(), command.globalVersion(),
                    organization.getId(), actorUserId, command.organizationEditable());
            skipped = target.existing();
        }

        OrganizationGlobalContentGrantJpaEntity grant;
        if (existing.isPresent()) {
            grant = existing.get();
            skipped = skipped || grant.getStatus() == GrantStatus.ACTIVE;
            grant.reconfigure(command.accessMode(), command.cloningAllowed(), command.organizationEditable(),
                    command.updatePolicy(), command.availableFrom(), command.expiresAt(),
                    actorUserId, clock.instant());
        } else {
            grant = OrganizationGlobalContentGrantJpaEntity.create(UUID.randomUUID().toString(), organization.getId(),
                    command.contentType(), global.internalId(), global.publicId(), command.globalVersion(),
                    command.distributionMode(), command.accessMode(), command.cloningAllowed(),
                    command.organizationEditable(), command.updatePolicy(), command.availableFrom(),
                    command.expiresAt(), actorUserId, clock.instant());
        }
        grant = grants.saveAndFlush(grant);
        audit.record(actorUserId, "GLOBAL_CONTENT_GRANTED", "GLOBAL_CONTENT",
                "Contenido global habilitado para una organización.", null, null,
                Map.of("grantPublicId", grant.getPublicId(), "organizationPublicId", organization.getPublicId(),
                        "contentType", command.contentType().name(), "globalContentPublicId", global.publicId(),
                        "globalVersion", command.globalVersion(), "distributionMode", command.distributionMode().name()),
                clock.instant());
        return new GrantOutcome(toView(grant, organization, target), grant.getId(), target, skipped);
    }

    @Transactional
    public GrantView disable(String grantPublicId, Long actorUserId) {
        OrganizationGlobalContentGrantJpaEntity grant = findGrant(grantPublicId);
        grant.disable(actorUserId, clock.instant());
        OrganizationJpaEntity organization = organizations.findById(grant.getOrganizationId())
                .orElseThrow(() -> new BusinessException("ORGANIZATION_NOT_FOUND", "La organización no existe."));
        audit.record(actorUserId, "GLOBAL_CONTENT_GRANT_DISABLED", "GLOBAL_CONTENT",
                "Acceso a contenido global deshabilitado.", null, null,
                Map.of("grantPublicId", grant.getPublicId(), "organizationPublicId", organization.getPublicId()),
                clock.instant());
        return toView(grants.save(grant), organization, replicated(grant));
    }

    @Transactional(readOnly = true)
    public List<GrantView> list(String organizationPublicId) {
        OrganizationJpaEntity organization = organization(organizationPublicId);
        return grants.findAllByOrganizationIdAndStatusOrderByEnabledAtDesc(organization.getId(), GrantStatus.ACTIVE)
                .stream().filter(grant -> grant.isOperational(clock.instant()))
                .map(grant -> toView(grant, organization, replicated(grant))).toList();
    }

    @Transactional(readOnly = true)
    public boolean hasActiveReference(Long organizationId, GlobalContentType type, Long globalContentId) {
        return grants.existsByOrganizationIdAndContentTypeAndGlobalContentIdAndStatus(
                organizationId, type, globalContentId, GrantStatus.ACTIVE);
    }

    private ReplicatedResource replicated(OrganizationGlobalContentGrantJpaEntity grant) {
        return grant.getDistributionMode() == DistributionMode.ORGANIZATION_COPY
                ? replication.existing(grant.getContentType(), grant.getGlobalContentId(), grant.getGlobalVersion(),
                        grant.getOrganizationId()).orElse(null)
                : null;
    }

    private OrganizationJpaEntity organization(String publicId) {
        OrganizationJpaEntity organization;
        try {
            String normalized = UUID.fromString(publicId == null ? "" : publicId.trim()).toString();
            organization = organizations.findByPublicId(normalized)
                    .orElseThrow(() -> new BusinessException("ORGANIZATION_NOT_FOUND", "La organización no existe."));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("ORGANIZATION_ID_INVALID", "El identificador de organización no es válido.");
        }
        if (organization.getOrganizationType() != OrganizationType.CUSTOMER) {
            throw new BusinessException("GLOBAL_CONTENT_CUSTOMER_REQUIRED",
                    "La organización GLOBAL no puede ser destino de una distribución.");
        }
        if (!organization.isOperational(LocalDate.now(clock))) {
            throw new BusinessException("ORGANIZATION_NOT_OPERATIONAL",
                    "La organización destino está inactiva o fuera de vigencia.");
        }
        return organization;
    }

    private OrganizationGlobalContentGrantJpaEntity findGrant(String publicId) {
        try {
            return grants.findByPublicId(UUID.fromString(publicId.trim()).toString())
                    .orElseThrow(() -> new BusinessException("GLOBAL_CONTENT_GRANT_NOT_FOUND",
                            "La habilitación solicitada no existe."));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("GLOBAL_CONTENT_GRANT_ID_INVALID",
                    "El identificador de habilitación no es válido.");
        }
    }

    private void validate(GrantCommand command) {
        if (command == null || command.contentType() == null || command.distributionMode() == null
                || command.accessMode() == null || command.updatePolicy() == null) {
            throw new BusinessException("GLOBAL_CONTENT_GRANT_INVALID",
                    "Completa la configuración de habilitación del contenido.");
        }
        if (command.globalVersion() <= 0) {
            throw new BusinessException("GLOBAL_CONTENT_VERSION_INVALID", "La versión global debe ser mayor a cero.");
        }
        if (command.availableFrom() != null && command.expiresAt() != null
                && !command.expiresAt().isAfter(command.availableFrom())) {
            throw new BusinessException("GLOBAL_CONTENT_GRANT_DATES_INVALID",
                    "La fecha de vencimiento debe ser posterior a la fecha de disponibilidad.");
        }
        if (command.distributionMode() == DistributionMode.GLOBAL_REFERENCE
                && command.organizationEditable()) {
            throw new BusinessException("GLOBAL_CONTENT_REFERENCE_READ_ONLY",
                    "Una referencia global no puede habilitar edición organizacional.");
        }
    }

    private GrantView toView(OrganizationGlobalContentGrantJpaEntity grant, OrganizationJpaEntity organization,
            ReplicatedResource target) {
        return new GrantView(grant.getPublicId(), organization.getPublicId(), organization.getName(),
                grant.getContentType(), grant.getGlobalContentPublicId(), grant.getGlobalVersion(), grant.getStatus(),
                grant.getDistributionMode(), grant.getAccessMode(), grant.isCloningAllowed(),
                grant.isOrganizationEditable(), grant.getUpdatePolicy(), grant.getAvailableFrom(), grant.getExpiresAt(),
                target == null ? null : target.publicId(), grant.getEnabledAt());
    }

    public record GrantOutcome(GrantView view, Long grantId, ReplicatedResource target, boolean skipped) {}
}
