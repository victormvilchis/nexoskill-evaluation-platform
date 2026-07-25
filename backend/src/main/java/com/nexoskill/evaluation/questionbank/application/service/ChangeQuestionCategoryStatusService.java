package com.nexoskill.evaluation.questionbank.application.service;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.questionbank.application.model.ChangeQuestionCategoryStatusCommand;
import com.nexoskill.evaluation.questionbank.application.model.QuestionCategorySummary;
import com.nexoskill.evaluation.questionbank.application.port.out.QuestionCatalogPort;
import com.nexoskill.evaluation.shared.domain.PublicIdNormalizer;
import java.time.Clock;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChangeQuestionCategoryStatusService {

    private final QuestionCatalogPort catalogPort;
    private final AuditLogPort auditLogPort;
    private final Clock clock;

    public ChangeQuestionCategoryStatusService(
            QuestionCatalogPort catalogPort,
            AuditLogPort auditLogPort,
            Clock clock) {
        this.catalogPort = catalogPort;
        this.auditLogPort = auditLogPort;
        this.clock = clock;
    }

    @Transactional
    public QuestionCategorySummary change(ChangeQuestionCategoryStatusCommand command) {
        String publicId = PublicIdNormalizer.requiredUuid(
                command.publicId(),
                "QUESTION_CATEGORY_REQUIRED",
                "La categoría es obligatoria."
        );
        QuestionCatalogPort.CategoryStatusChange result = catalogPort.changeCategoryStatus(
                publicId,
                command.targetStatus()
        );
        auditLogPort.record(
                command.actorUserId(),
                "QUESTION_CATEGORY_STATUS_CHANGED",
                "QUESTION_BANK",
                "Se actualizó el estado de una categoría del banco de preguntas.",
                command.ipAddress(),
                command.userAgent(),
                Map.of(
                        "categoryPublicId", result.category().publicId(),
                        "previousStatus", result.previousStatus().name(),
                        "currentStatus", result.category().status().name()
                ),
                clock.instant()
        );
        return result.category();
    }
}
