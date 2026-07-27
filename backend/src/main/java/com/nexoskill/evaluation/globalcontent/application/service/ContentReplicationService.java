package com.nexoskill.evaluation.globalcontent.application.service;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.globalcontent.application.model.GlobalContentModels.*;
import com.nexoskill.evaluation.globalcontent.application.port.out.GlobalContentResourcePort;
import com.nexoskill.evaluation.globalcontent.application.port.out.GlobalContentResourcePort.ResourceKey;
import com.nexoskill.evaluation.globalcontent.domain.model.GlobalContentType;
import com.nexoskill.evaluation.globalcontent.infrastructure.persistence.ContentReplicationLinkJpaEntity;
import com.nexoskill.evaluation.globalcontent.infrastructure.persistence.ContentReplicationLinkRepository;
import com.nexoskill.evaluation.organizations.domain.model.ContentScope;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContentReplicationService {
    private final GlobalContentResourcePort resources;
    private final GlobalContentVersionService versions;
    private final ContentReplicationLinkRepository links;
    private final AuditLogPort audit;
    private final Clock clock;

    public ContentReplicationService(GlobalContentResourcePort resources,
            GlobalContentVersionService versions,
            ContentReplicationLinkRepository links,
            AuditLogPort audit,
            Clock clock) {
        this.resources = resources;
        this.versions = versions;
        this.links = links;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public ReplicatedResource replicate(GlobalContentType type, Long globalContentId, long globalVersion,
            Long organizationId, Long actorUserId, boolean editable) {
        return replicateInternal(type, globalContentId, globalVersion, organizationId, actorUserId, editable,
                new LinkedHashSet<>());
    }

    @Transactional(readOnly = true)
    public Optional<ReplicatedResource> existing(GlobalContentType type, Long globalContentId, long globalVersion,
            Long organizationId) {
        return links.findByOrganizationIdAndContentTypeAndSourceGlobalContentIdAndSourceGlobalVersion(
                        organizationId, type, globalContentId, globalVersion)
                .map(this::view);
    }

    @Transactional
    public void markCustomized(GlobalContentType type, Long organizationId, Long targetContentId) {
        links.findByOrganizationIdAndContentTypeAndTargetContentId(organizationId, type, targetContentId)
                .ifPresent(link -> {
                    link.markCustomized();
                    links.save(link);
                    resources.markCustomized(type, targetContentId);
                });
    }

    private ReplicatedResource replicateInternal(GlobalContentType type, Long globalContentId, long globalVersion,
            Long organizationId, Long actorUserId, boolean editable, Set<ResourceKey> stack) {
        Optional<ContentReplicationLinkJpaEntity> existing = links
                .findByOrganizationIdAndContentTypeAndSourceGlobalContentIdAndSourceGlobalVersion(
                        organizationId, type, globalContentId, globalVersion);
        if (existing.isPresent()) return view(existing.get(), true);

        ContentResource global = resources.findByInternalId(type, globalContentId);
        if (global.scope() != ContentScope.GLOBAL) {
            throw new BusinessException("GLOBAL_CONTENT_SCOPE_REQUIRED",
                    "La replicación solamente admite contenido del catálogo global.");
        }
        if (!versions.isPublished(type, globalContentId, globalVersion)) {
            throw new BusinessException("GLOBAL_CONTENT_NOT_PUBLISHED",
                    "Solo el contenido global publicado puede distribuirse.");
        }

        ResourceKey key = new ResourceKey(type, globalContentId);
        if (!stack.add(key)) {
            throw new BusinessException("GLOBAL_CONTENT_DEPENDENCY_CYCLE",
                    "Se detectó un ciclo en la estructura global que se intenta replicar.");
        }
        try {
            Map<ResourceKey, Long> dependencyTargets = new LinkedHashMap<>();
            for (Dependency dependency : resources.dependencies(type, globalContentId)) {
                if (dependency.scope() != ContentScope.GLOBAL) {
                    throw new BusinessException("GLOBAL_CONTENT_DEPENDENCY_SCOPE_INVALID",
                            "La estructura global contiene una dependencia que sigue perteneciendo a una organización.");
                }
                long dependencyVersion = versions.latestPublishedVersion(dependency.contentType(), dependency.internalId());
                ReplicatedResource replicatedDependency = replicateInternal(dependency.contentType(), dependency.internalId(),
                        dependencyVersion, organizationId, actorUserId, editable, stack);
                dependencyTargets.put(new ResourceKey(dependency.contentType(), dependency.internalId()),
                        replicatedDependency.internalId());
            }

            ContentResource target = resources.copyToOrganization(type, globalContentId, organizationId,
                    globalVersion, dependencyTargets, actorUserId, editable);
            ContentReplicationLinkJpaEntity link = links.saveAndFlush(ContentReplicationLinkJpaEntity.create(
                    UUID.randomUUID().toString(), organizationId, type, globalContentId, global.publicId(),
                    globalVersion, target.internalId(), target.publicId(), actorUserId, clock.instant()));
            audit.record(actorUserId, "GLOBAL_CONTENT_REPLICATED", "GLOBAL_CONTENT",
                    "Contenido global replicado a una organización.", null, null,
                    Map.of("contentType", type.name(), "globalContentPublicId", global.publicId(),
                            "globalVersion", globalVersion, "organizationId", organizationId,
                            "targetContentPublicId", target.publicId()), clock.instant());
            return view(link, false);
        } finally {
            stack.remove(key);
        }
    }

    private ReplicatedResource view(ContentReplicationLinkJpaEntity link) {
        return view(link, true);
    }

    private ReplicatedResource view(ContentReplicationLinkJpaEntity link, boolean existing) {
        return new ReplicatedResource(link.getContentType(), link.getTargetContentId(),
                link.getTargetContentPublicId(), existing, link.isCustomized(), link.getSyncStatus());
    }
}
