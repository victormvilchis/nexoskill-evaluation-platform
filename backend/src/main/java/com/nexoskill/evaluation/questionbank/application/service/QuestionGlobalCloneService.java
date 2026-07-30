package com.nexoskill.evaluation.questionbank.application.service;
import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.globalcontent.application.model.GlobalContentModels.PromoteCommand;
import com.nexoskill.evaluation.globalcontent.application.service.ContentPromotionService;
import com.nexoskill.evaluation.globalcontent.domain.model.DuplicateResolution;
import com.nexoskill.evaluation.globalcontent.domain.model.GlobalContentType;
import com.nexoskill.evaluation.globalcontent.infrastructure.persistence.GlobalContentPromotionRepository;
import com.nexoskill.evaluation.organizations.application.TenantContextResolver;
import com.nexoskill.evaluation.organizations.domain.model.ContentScope;
import com.nexoskill.evaluation.questionbank.infrastructure.persistence.QuestionTagStore;
import com.nexoskill.evaluation.questionbank.infrastructure.persistence.SpringDataQuestionRepository;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import com.nexoskill.evaluation.shared.domain.PublicIdNormalizer;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class QuestionGlobalCloneService {
    private final SpringDataQuestionRepository questions;
    private final QuestionTagStore tagStore;
    private final GlobalContentPromotionRepository promotions;
    private final ContentPromotionService promotionService;
    private final TenantContextResolver tenantContextResolver;
    private final HttpServletRequest request;
    private final AuditLogPort audit;
    private final Clock clock;
    @Autowired
    public QuestionGlobalCloneService(SpringDataQuestionRepository questions,
            QuestionTagStore tagStore,
            GlobalContentPromotionRepository promotions,
            ContentPromotionService promotionService,
            TenantContextResolver tenantContextResolver,
            HttpServletRequest request,
            AuditLogPort audit,
            Clock clock) {
        this.questions = questions;
        this.tagStore = tagStore;
        this.promotions = promotions;
        this.promotionService = promotionService;
        this.tenantContextResolver = tenantContextResolver;
        this.request = request;
        this.audit = audit;
        this.clock = clock;
    }
    public QuestionGlobalCloneService(SpringDataQuestionRepository questions,
            GlobalContentPromotionRepository promotions,
            ContentPromotionService promotionService,
            TenantContextResolver tenantContextResolver,
            HttpServletRequest request,
            AuditLogPort audit,
            Clock clock) {
        this(questions, null, promotions, promotionService, tenantContextResolver,
                request, audit, clock);
    }

    @Transactional(readOnly = true)
    public com.nexoskill.evaluation.globalcontent.application.model.GlobalContentModels.PromotionPreview preview(String questionPublicId) {
        ensureGlobalAdministrator();
        return promotionService.preview(GlobalContentType.QUESTION, questionPublicId);
    }
    @Transactional
    public CloneResult cloneToGlobal(String questionPublicId, boolean includeDependencies,
                                     String notes, Long actorUserId) {
        ensureGlobalAdministrator();
        String normalized = PublicIdNormalizer.requiredUuid(questionPublicId,
                "QUESTION_ID_INVALID", "La pregunta indicada no es válida.");
        var source = questions.findByPublicId(normalized)
                .orElseThrow(() -> new BusinessException("QUESTION_NOT_FOUND",
                        "La pregunta solicitada no existe."));
        if (source.getContentScope() != ContentScope.ORGANIZATION || source.getOwnerOrganizationId() == null) {
            throw new BusinessException("QUESTION_GLOBAL_CLONE_SOURCE_INVALID",
                    "Solo una pregunta organizacional puede clonarse al catálogo global.");
        }
        if (promotions.findByContentTypeAndSourceContentIdAndSourceVersion(
                GlobalContentType.QUESTION, source.getId(), source.getVersion()).isPresent()) {
            throw new BusinessException("QUESTION_GLOBAL_CLONE_EXISTS",
                    "Ya existe una pregunta global relacionada con este contenido.");
        }
        var promotion = promotionService.promote(new PromoteCommand(GlobalContentType.QUESTION,
                source.getPublicId(), includeDependencies, DuplicateResolution.CREATE_DISTINCT,
                null, notes), actorUserId);
        var global = questions.findByPublicId(promotion.globalContentPublicId())
                .orElseThrow(() -> new BusinessException("QUESTION_GLOBAL_CLONE_FAILED",
                        "No fue posible localizar la copia global creada."));
        global.markClonedFromOrganization(source.getOwnerOrganizationId(), source.getId(),
                source.getVersion(), actorUserId, clock.instant());
        questions.saveAndFlush(global);
        if (tagStore != null) {
            tagStore.copy(source.getId(), global.getId(), ContentScope.GLOBAL,
                    global.getOwnerOrganizationId(), actorUserId, clock.instant());
        }
        audit.record(actorUserId, "QUESTION_CLONED_TO_GLOBAL", "QUESTION_BANK",
                "Se clonó una pregunta organizacional al Banco de Preguntas Global.",
                null, null, Map.of("sourceQuestionPublicId", source.getPublicId(),
                        "globalQuestionPublicId", global.getPublicId(),
                        "sourceOrganizationId", source.getOwnerOrganizationId()), clock.instant());
        return new CloneResult(promotion.publicId(), source.getPublicId(), global.getPublicId());
    }
    private void ensureGlobalAdministrator() {
        var tenant = tenantContextResolver.resolve(request);
        if (!tenant.globalAdministrator()) {
            throw new BusinessException("QUESTION_GLOBAL_CLONE_FORBIDDEN",
                    "Solo el Administrador global puede clonar preguntas al catálogo global.");
        }
    }
    public record CloneResult(String promotionPublicId, String sourceQuestionPublicId,
                              String globalQuestionPublicId) { }
}
