package com.nexoskill.evaluation.questionbank.infrastructure.persistence;

import com.nexoskill.evaluation.organizations.domain.model.ContentScope;
import com.nexoskill.evaluation.organizations.domain.model.TenantContext;
import com.nexoskill.evaluation.questionbank.application.model.QuestionMediaView;
import com.nexoskill.evaluation.questionbank.application.port.out.QuestionMediaPort;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import com.nexoskill.evaluation.shared.domain.PublicIdNormalizer;
import java.time.Clock;
import org.springframework.stereotype.Component;

@Component
public class OracleQuestionMediaAdapter implements QuestionMediaPort {
    private final SpringDataQuestionMediaRepository repository;
    private final QuestionMediaAccessPolicy mediaAccessPolicy;
    private final Clock clock;

    public OracleQuestionMediaAdapter(SpringDataQuestionMediaRepository repository,
            QuestionMediaAccessPolicy mediaAccessPolicy, Clock clock) {
        this.repository = repository;
        this.mediaAccessPolicy = mediaAccessPolicy;
        this.clock = clock;
    }

    @Override
    public QuestionMediaView save(String publicId, String storageKey, String originalName, String contentType,
            long size, String checksum, ContentScope contentScope, Long ownerOrganizationId, Long actor) {
        return view(repository.saveAndFlush(QuestionMediaJpaEntity.create(publicId, storageKey, originalName,
                contentType, size, checksum, contentScope, ownerOrganizationId, actor, clock.instant())));
    }

    @Override
    public MediaData getAuthorized(String publicId, Long actorUserId, TenantContext tenant) {
        QuestionMediaJpaEntity entity = find(publicId);
        mediaAccessPolicy.assertAccessible(entity, actorUserId, tenant);
        return new MediaData(view(entity), entity.getStorageKey());
    }

    private QuestionMediaJpaEntity find(String publicId) {
        String normalized = PublicIdNormalizer.requiredUuid(publicId, "QUESTION_MEDIA_INVALID",
                "La imagen indicada no es válida.");
        return repository.findByPublicId(normalized).orElseThrow(
                () -> new BusinessException("QUESTION_MEDIA_NOT_FOUND", "La imagen solicitada no existe."));
    }

    private QuestionMediaView view(QuestionMediaJpaEntity entity) {
        return new QuestionMediaView(entity.getPublicId(), entity.getOriginalName(), entity.getContentType(),
                entity.getSize(), "/api/v1/question-media/" + entity.getPublicId());
    }
}
