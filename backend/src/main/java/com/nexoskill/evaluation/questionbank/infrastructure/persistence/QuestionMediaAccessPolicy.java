package com.nexoskill.evaluation.questionbank.infrastructure.persistence;

import com.nexoskill.evaluation.globalcontent.application.service.GlobalContentAccessPolicy;
import com.nexoskill.evaluation.globalcontent.domain.model.GlobalContentType;
import com.nexoskill.evaluation.organizations.domain.model.ContentScope;
import com.nexoskill.evaluation.organizations.domain.model.TenantContext;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Centraliza el aislamiento organizacional de archivos asociados con preguntas. */
@Component
public class QuestionMediaAccessPolicy {
    private final SpringDataQuestionRepository questions;
    private final GlobalContentAccessPolicy contentAccessPolicy;

    public QuestionMediaAccessPolicy(SpringDataQuestionRepository questions,
            GlobalContentAccessPolicy contentAccessPolicy) {
        this.questions = questions;
        this.contentAccessPolicy = contentAccessPolicy;
    }

    public void assertAccessible(QuestionMediaJpaEntity media, Long actorUserId, TenantContext tenant) {
        if (media == null || tenant == null || actorUserId == null) {
            throw notFound();
        }
        if (tenant.globalAdministrator() && tenant.globalScope()) {
            return;
        }
        if (!tenant.hasOrganization() || tenant.globalScope()) {
            throw notFound();
        }
        if (media.getContentScope() == ContentScope.ORGANIZATION
                && Objects.equals(media.getOwnerOrganizationId(), tenant.organizationId())) {
            return;
        }
        if (Objects.equals(media.getCreatedBy(), actorUserId)
                && Objects.equals(media.getOwnerOrganizationId(), tenant.organizationId())) {
            return;
        }
        boolean readableQuestionUsesMedia = questions.findAllUsingMedia(media.getId()).stream()
                .anyMatch(question -> contentAccessPolicy.canRead(GlobalContentType.QUESTION, question.getId(),
                        question.getContentScope(), question.getOwnerOrganizationId(), tenant));
        if (!readableQuestionUsesMedia) {
            throw notFound();
        }
    }


    public void assertAssignable(QuestionMediaJpaEntity media, Long actorUserId, TenantContext tenant,
            ContentScope targetScope, Long targetOrganizationId) {
        assertAccessible(media, actorUserId, tenant);
        if (targetScope == null || targetOrganizationId == null) {
            throw notFound();
        }
        if (targetScope == ContentScope.GLOBAL) {
            if (media.getContentScope() != ContentScope.GLOBAL
                    || !Objects.equals(media.getOwnerOrganizationId(), targetOrganizationId)) {
                throw new BusinessException("QUESTION_MEDIA_SCOPE_MISMATCH",
                        "La imagen no pertenece al mismo alcance que la pregunta.");
            }
            return;
        }
        if (media.getContentScope() == ContentScope.ORGANIZATION
                && !Objects.equals(media.getOwnerOrganizationId(), targetOrganizationId)) {
            throw notFound();
        }
    }

    private BusinessException notFound() {
        return new BusinessException("QUESTION_MEDIA_NOT_FOUND", "La imagen solicitada no existe.");
    }
}
