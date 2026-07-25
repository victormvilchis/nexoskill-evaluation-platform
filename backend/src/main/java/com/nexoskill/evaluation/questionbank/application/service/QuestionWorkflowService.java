package com.nexoskill.evaluation.questionbank.application.service;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.questionbank.application.model.QuestionDetail;
import com.nexoskill.evaluation.questionbank.application.model.QuestionOptionCommand;
import com.nexoskill.evaluation.questionbank.application.model.TransitionQuestionCommand;
import com.nexoskill.evaluation.questionbank.application.port.out.QuestionBankPort;
import com.nexoskill.evaluation.questionbank.domain.model.QuestionStatus;
import com.nexoskill.evaluation.questionbank.domain.model.QuestionTypeCode;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import java.time.Clock;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuestionWorkflowService {

    private final QuestionBankPort questionBankPort;
    private final QuestionDraftValidator validator;
    private final AuditLogPort auditLogPort;
    private final Clock clock;

    public QuestionWorkflowService(
            QuestionBankPort questionBankPort,
            QuestionDraftValidator validator,
            AuditLogPort auditLogPort,
            Clock clock) {
        this.questionBankPort = questionBankPort;
        this.validator = validator;
        this.auditLogPort = auditLogPort;
        this.clock = clock;
    }

    @Transactional
    public QuestionDetail transition(TransitionQuestionCommand command) {
        QuestionStatus target = parseStatus(command.targetStatus());
        requirePermission(command, target);
        QuestionDetail before = questionBankPort.getByPublicId(command.publicId());
        if (target == QuestionStatus.PUBLISHED) {
            validator.validateForPublication(
                    QuestionTypeCode.valueOf(before.typeCode()),
                    before.statement(), before.explanation(),
                    before.options().stream()
                            .map(option -> new QuestionOptionCommand(option.text(), option.correct()))
                            .toList()
            );
        }

        QuestionBankPort.TransitionResult result = questionBankPort.transition(
                command.publicId(), target, command.expectedEntityVersion(), command.actorUserId());

        String eventType = switch (result.currentStatus()) {
            case UNDER_REVIEW -> "QUESTION_SUBMITTED_FOR_REVIEW";
            case APPROVED -> "QUESTION_APPROVED";
            case PUBLISHED -> "QUESTION_PUBLISHED";
            case ARCHIVED -> "QUESTION_ARCHIVED";
            case DRAFT -> "QUESTION_RETURNED_TO_DRAFT";
        };
        auditLogPort.record(
                command.actorUserId(), eventType, "QUESTION_BANK",
                "Se cambió el estado editorial de la pregunta.",
                command.ipAddress(), command.userAgent(),
                Map.of(
                        "questionPublicId", result.question().publicId(),
                        "previousStatus", result.previousStatus().name(),
                        "currentStatus", result.currentStatus().name(),
                        "versionNumber", result.question().versionNumber()
                ), clock.instant());
        return result.question();
    }

    private void requirePermission(TransitionQuestionCommand command, QuestionStatus target) {
        String required = switch (target) {
            case UNDER_REVIEW, DRAFT -> "QUESTION_REVIEW";
            case APPROVED -> "QUESTION_APPROVE";
            case PUBLISHED -> "QUESTION_PUBLISH";
            case ARCHIVED -> "QUESTION_ARCHIVE";
        };
        if (!command.actorPermissions().contains(required)) {
            throw new BusinessException(
                    "QUESTION_TRANSITION_FORBIDDEN",
                    "No tienes permiso para realizar esta transición editorial."
            );
        }
    }

    private QuestionStatus parseStatus(String value) {
        if (value == null || value.isBlank()) throw new BusinessException(
                "QUESTION_TARGET_STATUS_REQUIRED", "El estado destino es obligatorio.");
        try {
            return QuestionStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("QUESTION_TARGET_STATUS_INVALID",
                    "El estado destino no es válido.");
        }
    }
}
