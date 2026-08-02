package com.nexoskill.evaluation.globalcontent.application.service;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.globalcontent.application.model.GlobalContentModels.*;
import com.nexoskill.evaluation.globalcontent.application.port.out.GlobalContentResourcePort;
import com.nexoskill.evaluation.globalcontent.domain.model.GlobalContentType;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import com.nexoskill.evaluation.shared.interfaces.rest.PaginationParameters;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GlobalContentReviewService {
    private final GlobalContentResourcePort resources;
    private final AuditLogPort audit;
    private final Clock clock;

    public GlobalContentReviewService(GlobalContentResourcePort resources, AuditLogPort audit, Clock clock) {
        this.resources = resources;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public ReviewPage review(ReviewFilter filter, Long actorUserId) {
        if ((filter.organizationPublicId() == null || filter.organizationPublicId().isBlank())
                && filter.scope() != com.nexoskill.evaluation.organizations.domain.model.ContentScope.GLOBAL) {
            throw new BusinessException("GLOBAL_CONTENT_REVIEW_CONTEXT_REQUIRED",
                    "Selecciona una organización o el alcance GLOBAL para realizar la consulta transversal.");
        }
        PaginationParameters.validate(filter.page(), filter.size());
        ReviewPage result = resources.review(filter);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("organizationPublicId", filter.organizationPublicId());
        data.put("contentType", filter.contentType() == null ? null : filter.contentType().name());
        data.put("scope", filter.scope() == null ? null : filter.scope().name());
        data.put("status", filter.status());
        data.put("page", result.page());
        data.put("size", result.size());
        data.put("totalElements", result.totalElements());
        audit.record(actorUserId, "GLOBAL_CONTENT_TRANSVERSAL_REVIEW", "GLOBAL_CONTENT",
                "Consulta transversal de contenido organizacional y global.", null, null, data, clock.instant());
        return result;
    }

    @Transactional
    public ContentResource get(GlobalContentType type, String publicId, Long actorUserId) {
        ContentResource resource = resources.find(type, publicId);
        audit.record(actorUserId, "GLOBAL_CONTENT_TRANSVERSAL_DETAIL", "GLOBAL_CONTENT",
                "Consulta transversal del detalle de contenido.", null, null,
                Map.of("contentType", type.name(), "contentPublicId", publicId,
                        "scope", resource.scope().name()), clock.instant());
        return resource;
    }
}
