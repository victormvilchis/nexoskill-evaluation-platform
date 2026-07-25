package com.nexoskill.evaluation.questionbank.application.service;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.questionbank.application.model.QuestionDetail;
import com.nexoskill.evaluation.questionbank.application.model.TransitionQuestionCommand;
import com.nexoskill.evaluation.questionbank.application.port.out.QuestionBankPort;
import com.nexoskill.evaluation.questionbank.domain.model.QuestionStatus;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import com.nexoskill.evaluation.shared.domain.PublicIdNormalizer;
import java.time.Clock;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuestionWorkflowService {

    private final QuestionBankPort questionBankPort;
    private final AuditLogPort auditLogPort;
    private final Clock clock;

    public QuestionWorkflowService(
            QuestionBankPort questionBankPort,
            QuestionDraftValidator ignoredValidator,
            AuditLogPort auditLogPort,
            Clock clock) {
        this.questionBankPort = questionBankPort;
        this.auditLogPort = auditLogPort;
        this.clock = clock;
    }

    @Transactional
    public QuestionDetail transition(TransitionQuestionCommand command) {
        QuestionStatus target = parseStatus(command.targetStatus());
        if (target != QuestionStatus.ARCHIVED) {
            throw new BusinessException(
                    "QUESTION_WORKFLOW_SIMPLIFIED",
                    "Las preguntas se publican automáticamente. Solo está disponible la acción de archivar."
            );
        }
        if (!command.actorPermissions().contains("QUESTION_ARCHIVE")) {
            throw new BusinessException(
                    "QUESTION_TRANSITION_FORBIDDEN",
                    "No tienes permiso para archivar preguntas."
            );
        }

        String questionPublicId = PublicIdNormalizer.requiredUuid(
                command.publicId(), "QUESTION_ID_REQUIRED", "La pregunta es obligatoria."
        );
        QuestionBankPort.TransitionResult result = questionBankPort.transition(
                questionPublicId,
                QuestionStatus.ARCHIVED,
                command.expectedEntityVersion(),
                command.actorUserId()
        );

        auditLogPort.record(
                command.actorUserId(),
                "QUESTION_ARCHIVED",
                "QUESTION_BANK",
                "Se archivó la pregunta.",
                command.ipAddress(),
                command.userAgent(),
                Map.of(
                        "questionPublicId", result.question().publicId(),
                        "previousStatus", result.previousStatus().name(),
                        "currentStatus", result.currentStatus().name(),
                        "versionNumber", result.question().versionNumber()
                ),
                clock.instant()
        );
        return result.question();
    }

    private QuestionStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(
                    "QUESTION_TARGET_STATUS_REQUIRED",
                    "El estado destino es obligatorio."
            );
        }
        try {
            return QuestionStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    "QUESTION_TARGET_STATUS_INVALID",
                    "El estado destino no es válido."
            );
        }
    }
}
