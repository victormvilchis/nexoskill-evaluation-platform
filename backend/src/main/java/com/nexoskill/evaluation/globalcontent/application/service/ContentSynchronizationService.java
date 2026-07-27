package com.nexoskill.evaluation.globalcontent.application.service;

import com.nexoskill.evaluation.globalcontent.application.model.GlobalContentModels.ReplicatedResource;
import com.nexoskill.evaluation.globalcontent.domain.model.GlobalContentType;
import com.nexoskill.evaluation.globalcontent.infrastructure.persistence.ContentReplicationLinkRepository;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContentSynchronizationService {
    private final ContentReplicationLinkRepository links;
    private final ContentReplicationService replication;

    public ContentSynchronizationService(ContentReplicationLinkRepository links,
            ContentReplicationService replication) {
        this.links = links;
        this.replication = replication;
    }

    @Transactional(readOnly = true)
    public ReplicatedResource status(GlobalContentType type, Long organizationId, Long targetContentId) {
        var link = links.findByOrganizationIdAndContentTypeAndTargetContentId(organizationId, type, targetContentId)
                .orElseThrow(() -> new BusinessException("GLOBAL_CONTENT_REPLICATION_LINK_NOT_FOUND",
                        "El contenido no está vinculado a una versión global."));
        return new ReplicatedResource(link.getContentType(), link.getTargetContentId(),
                link.getTargetContentPublicId(), true, link.isCustomized(), link.getSyncStatus());
    }

    @Transactional
    public void markCustomized(GlobalContentType type, Long organizationId, Long targetContentId) {
        replication.markCustomized(type, organizationId, targetContentId);
    }

    @Transactional
    public void requestAutomaticSynchronization(GlobalContentType type, Long organizationId, Long targetContentId) {
        var link = links.findByOrganizationIdAndContentTypeAndTargetContentId(organizationId, type, targetContentId)
                .orElseThrow(() -> new BusinessException("GLOBAL_CONTENT_REPLICATION_LINK_NOT_FOUND",
                        "El contenido no está vinculado a una versión global."));
        if (link.isCustomized()) {
            throw new BusinessException("GLOBAL_CONTENT_CUSTOMIZED_COPY",
                    "La copia fue personalizada y requiere una actualización manual controlada.");
        }
        link.markUpdateAvailable();
        links.save(link);
    }
}
