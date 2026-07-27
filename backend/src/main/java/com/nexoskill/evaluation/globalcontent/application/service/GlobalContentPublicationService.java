package com.nexoskill.evaluation.globalcontent.application.service;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.globalcontent.application.model.GlobalContentModels.Dependency;
import com.nexoskill.evaluation.globalcontent.application.model.GlobalContentModels.PromotionView;
import com.nexoskill.evaluation.globalcontent.application.port.out.GlobalContentResourcePort;
import com.nexoskill.evaluation.globalcontent.domain.model.EditorialStatus;
import com.nexoskill.evaluation.globalcontent.infrastructure.persistence.*;
import com.nexoskill.evaluation.organizations.domain.model.ContentScope;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import java.time.Clock;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GlobalContentPublicationService {
    private final ContentPromotionService promotionService;
    private final GlobalContentPromotionRepository promotions;
    private final GlobalContentVersionRepository versions;
    private final GlobalContentResourcePort resources;
    private final AuditLogPort audit;
    private final Clock clock;

    public GlobalContentPublicationService(ContentPromotionService promotionService,
            GlobalContentPromotionRepository promotions,
            GlobalContentVersionRepository versions,
            GlobalContentResourcePort resources,
            AuditLogPort audit,
            Clock clock) {
        this.promotionService = promotionService;
        this.promotions = promotions;
        this.versions = versions;
        this.resources = resources;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public PromotionView submitForReview(String promotionPublicId, Long actorUserId) {
        GlobalContentPromotionJpaEntity promotion = promotionService.findPromotion(promotionPublicId);
        try {
            promotion.submitForReview(actorUserId, clock.instant());
            version(promotion).markUnderReview();
        } catch (IllegalStateException exception) {
            throw transitionError();
        }
        audit.record(actorUserId, "GLOBAL_CONTENT_SUBMITTED_FOR_REVIEW", "GLOBAL_CONTENT",
                "Contenido global enviado a revisión editorial.", null, null,
                Map.of("promotionPublicId", promotion.getPublicId()), clock.instant());
        return promotionService.view(promotions.save(promotion));
    }

    @Transactional
    public PromotionView publish(String promotionPublicId, Long actorUserId) {
        GlobalContentPromotionJpaEntity promotion = promotionService.findPromotion(promotionPublicId);
        ensureDependenciesPublished(promotion);
        try {
            promotion.publish(actorUserId, clock.instant());
            GlobalContentVersionJpaEntity version = version(promotion);
            version.publish(actorUserId, clock.instant());
            versions.save(version);
            resources.changeOperationalStatus(promotion.getContentType(), promotion.getGlobalContentId(),
                    "ACTIVE", actorUserId);
        } catch (IllegalStateException exception) {
            throw transitionError();
        }
        promotions.save(promotion);
        audit.record(actorUserId, "GLOBAL_CONTENT_PUBLISHED", "GLOBAL_CONTENT",
                "Contenido global publicado.", null, null,
                Map.of("promotionPublicId", promotion.getPublicId(),
                        "globalContentPublicId", promotion.getGlobalContentPublicId(),
                        "globalVersion", promotion.getGlobalVersion()), clock.instant());
        return promotionService.view(promotion);
    }

    @Transactional
    public PromotionView reject(String promotionPublicId, String reason, Long actorUserId) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("GLOBAL_CONTENT_REJECTION_REASON_REQUIRED",
                    "Escribe el motivo del rechazo.");
        }
        GlobalContentPromotionJpaEntity promotion = promotionService.findPromotion(promotionPublicId);
        try {
            promotion.reject(actorUserId, reason.trim(), clock.instant());
            GlobalContentVersionJpaEntity version = version(promotion);
            version.reject();
            versions.save(version);
        } catch (IllegalStateException exception) {
            throw transitionError();
        }
        promotions.save(promotion);
        audit.record(actorUserId, "GLOBAL_CONTENT_REJECTED", "GLOBAL_CONTENT",
                "Promoción global rechazada.", null, null,
                Map.of("promotionPublicId", promotion.getPublicId(), "reason", reason.trim()), clock.instant());
        return promotionService.view(promotion);
    }

    @Transactional
    public PromotionView archive(String promotionPublicId, Long actorUserId) {
        GlobalContentPromotionJpaEntity promotion = promotionService.findPromotion(promotionPublicId);
        promotion.archive(actorUserId, clock.instant());
        GlobalContentVersionJpaEntity version = version(promotion);
        version.archive();
        versions.save(version);
        resources.changeOperationalStatus(promotion.getContentType(), promotion.getGlobalContentId(),
                "ARCHIVED", actorUserId);
        promotions.save(promotion);
        audit.record(actorUserId, "GLOBAL_CONTENT_ARCHIVED", "GLOBAL_CONTENT",
                "Contenido global archivado.", null, null,
                Map.of("promotionPublicId", promotion.getPublicId()), clock.instant());
        return promotionService.view(promotion);
    }

    private void ensureDependenciesPublished(GlobalContentPromotionJpaEntity promotion) {
        for (Dependency dependency : resources.dependencies(promotion.getContentType(), promotion.getGlobalContentId())) {
            if (dependency.scope() != ContentScope.GLOBAL) {
                throw new BusinessException("GLOBAL_CONTENT_DEPENDENCY_SCOPE_INVALID",
                        "El contenido global conserva una dependencia organizacional incompatible.");
            }
            boolean published = versions.findFirstByContentTypeAndContentIdAndStatusOrderByVersionNumberDesc(
                            dependency.contentType(), dependency.internalId(), EditorialStatus.PUBLISHED)
                    .isPresent();
            if (!published) {
                var resource = resources.findByInternalId(dependency.contentType(), dependency.internalId());
                published = "ACTIVE".equalsIgnoreCase(resource.status());
            }
            if (!published) {
                throw new BusinessException("GLOBAL_CONTENT_DEPENDENCY_NOT_PUBLISHED",
                        "Publica primero la dependencia «" + dependency.name() + "».");
            }
        }
    }

    private GlobalContentVersionJpaEntity version(GlobalContentPromotionJpaEntity promotion) {
        return versions.findByContentTypeAndContentIdAndVersionNumber(
                        promotion.getContentType(), promotion.getGlobalContentId(), promotion.getGlobalVersion())
                .orElseThrow(() -> new BusinessException("GLOBAL_CONTENT_VERSION_NOT_FOUND",
                        "No se encontró la versión editorial de la promoción."));
    }

    private static BusinessException transitionError() {
        return new BusinessException("GLOBAL_CONTENT_EDITORIAL_TRANSITION_INVALID",
                "La transición editorial solicitada no es válida para el estado actual.");
    }
}
