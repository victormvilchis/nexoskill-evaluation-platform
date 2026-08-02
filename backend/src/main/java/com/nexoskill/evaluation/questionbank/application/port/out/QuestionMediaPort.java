package com.nexoskill.evaluation.questionbank.application.port.out;

import com.nexoskill.evaluation.organizations.domain.model.ContentScope;
import com.nexoskill.evaluation.organizations.domain.model.TenantContext;
import com.nexoskill.evaluation.questionbank.application.model.QuestionMediaView;

public interface QuestionMediaPort {
    QuestionMediaView save(String publicId, String storageKey, String originalName, String contentType, long size,
            String checksum, ContentScope contentScope, Long ownerOrganizationId, Long actorUserId);

    MediaData getAuthorized(String publicId, Long actorUserId, TenantContext tenant);

    record MediaData(QuestionMediaView view, String storageKey) {
    }
}
