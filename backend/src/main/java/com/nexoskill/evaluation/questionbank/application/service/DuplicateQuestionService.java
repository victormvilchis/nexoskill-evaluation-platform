package com.nexoskill.evaluation.questionbank.application.service;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.questionbank.application.model.DuplicateQuestionCommand;
import com.nexoskill.evaluation.questionbank.application.model.QuestionDetail;
import com.nexoskill.evaluation.questionbank.application.port.out.QuestionBankPort;
import com.nexoskill.evaluation.shared.domain.PublicIdNormalizer;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DuplicateQuestionService {

    private final QuestionBankPort questionBankPort;
    private final AuditLogPort auditLogPort;
    private final Clock clock;

    public DuplicateQuestionService(
            QuestionBankPort questionBankPort,
            AuditLogPort auditLogPort,
            Clock clock) {
        this.questionBankPort = questionBankPort;
        this.auditLogPort = auditLogPort;
        this.clock = clock;
    }

    @Transactional
    public QuestionDetail duplicate(DuplicateQuestionCommand command) {
        String sourcePublicId = PublicIdNormalizer.requiredUuid(
                command.publicId(), "QUESTION_ID_REQUIRED", "La pregunta es obligatoria."
        );
        QuestionDetail duplicated = questionBankPort.duplicate(
                sourcePublicId, UUID.randomUUID().toString(), command.actorUserId());
        auditLogPort.record(
                command.actorUserId(), "QUESTION_DUPLICATED", "QUESTION_BANK",
                "Se duplicó y publicó una nueva pregunta.",
                command.ipAddress(), command.userAgent(),
                Map.of(
                        "sourceQuestionPublicId", sourcePublicId,
                        "newQuestionPublicId", duplicated.publicId()
                ), clock.instant());
        return duplicated;
    }
}
