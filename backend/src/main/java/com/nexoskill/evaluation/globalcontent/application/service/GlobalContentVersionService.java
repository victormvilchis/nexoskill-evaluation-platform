package com.nexoskill.evaluation.globalcontent.application.service;

import com.nexoskill.evaluation.globalcontent.application.model.GlobalContentModels.VersionView;
import com.nexoskill.evaluation.globalcontent.application.port.out.GlobalContentResourcePort;
import com.nexoskill.evaluation.globalcontent.domain.model.EditorialStatus;
import com.nexoskill.evaluation.globalcontent.domain.model.GlobalContentType;
import com.nexoskill.evaluation.globalcontent.infrastructure.persistence.GlobalContentVersionJpaEntity;
import com.nexoskill.evaluation.globalcontent.infrastructure.persistence.GlobalContentVersionRepository;
import com.nexoskill.evaluation.organizations.domain.model.ContentScope;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GlobalContentVersionService {
    private final GlobalContentVersionRepository versions;
    private final GlobalContentResourcePort resources;
    private final Clock clock;

    public GlobalContentVersionService(GlobalContentVersionRepository versions,
            GlobalContentResourcePort resources, Clock clock) {
        this.versions = versions;
        this.resources = resources;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public boolean isPublished(GlobalContentType type, Long contentId, long requestedVersion) {
        return versions.findByContentTypeAndContentIdAndVersionNumber(type, contentId, requestedVersion)
                .map(version -> version.getStatus() == EditorialStatus.PUBLISHED)
                .orElseGet(() -> {
                    var resource = resources.findByInternalId(type, contentId);
                    return resource.scope() == ContentScope.GLOBAL
                            && "ACTIVE".equalsIgnoreCase(resource.status())
                            && requestedVersion == Math.max(1, resource.version());
                });
    }

    @Transactional(readOnly = true)
    public long latestPublishedVersion(GlobalContentType type, Long contentId) {
        return versions.findFirstByContentTypeAndContentIdAndStatusOrderByVersionNumberDesc(
                type, contentId, EditorialStatus.PUBLISHED)
                .map(GlobalContentVersionJpaEntity::getVersionNumber)
                .orElseGet(() -> Math.max(1, resources.findByInternalId(type, contentId).version()));
    }

    @Transactional
    public VersionView createVersion(GlobalContentType type, String contentPublicId, String notes, Long actorUserId) {
        var resource = resources.find(type, contentPublicId);
        if (resource.scope() != ContentScope.GLOBAL) {
            throw new BusinessException("GLOBAL_CONTENT_SCOPE_REQUIRED",
                    "Solo el contenido global puede generar versiones editoriales.");
        }
        long next = versions.findFirstByContentTypeAndContentIdOrderByVersionNumberDesc(type, resource.internalId())
                .map(current -> current.getVersionNumber() + 1)
                .orElse(1L);
        var version = versions.save(GlobalContentVersionJpaEntity.create(UUID.randomUUID().toString(), type,
                resource.internalId(), resource.publicId(), next, null, resource.functionalHash(), notes,
                actorUserId, clock.instant()));
        return view(version);
    }

    @Transactional(readOnly = true)
    public List<VersionView> list(GlobalContentType type, String contentPublicId) {
        var resource = resources.find(type, contentPublicId);
        return versions.findAllByContentTypeAndContentIdOrderByVersionNumberDesc(type, resource.internalId())
                .stream().map(this::view).toList();
    }

    VersionView view(GlobalContentVersionJpaEntity value) {
        return new VersionView(value.getPublicId(), value.getContentType(), value.getContentPublicId(),
                value.getVersionNumber(), value.getStatus(), value.getFunctionalHash(), value.getChangeNotes(),
                value.getCreatedBy(), value.getCreatedAt(), value.getPublishedBy(), value.getPublishedAt());
    }
}
