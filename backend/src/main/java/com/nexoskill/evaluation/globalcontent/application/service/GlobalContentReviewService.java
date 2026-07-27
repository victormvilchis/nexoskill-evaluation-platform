package com.nexoskill.evaluation.globalcontent.application.service;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.globalcontent.application.model.GlobalContentModels.*;
import com.nexoskill.evaluation.globalcontent.application.port.out.GlobalContentResourcePort;
import com.nexoskill.evaluation.globalcontent.domain.model.GlobalContentType;
import com.nexoskill.evaluation.shared.domain.BusinessException;
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
        int page = Math.max(0, filter.page());
        int size = Math.min(Math.max(1, filter.size()), 100);
        List<ContentResource> all = resources.review(filter);
        int from = Math.min(page * size, all.size());
        int to = Math.min(from + size, all.size());
        int totalPages = all.isEmpty() ? 0 : (int) Math.ceil(all.size() / (double) size);
        ReviewPage result = new ReviewPage(List.copyOf(all.subList(from, to)), page, size, all.size(), totalPages);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("organizationPublicId", filter.organizationPublicId());
        data.put("contentType", filter.contentType() == null ? null : filter.contentType().name());
        data.put("scope", filter.scope() == null ? null : filter.scope().name());
        data.put("status", filter.status());
        data.put("page", page);
        data.put("size", size);
        data.put("totalElements", all.size());
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
