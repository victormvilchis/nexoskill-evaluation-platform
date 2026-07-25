package com.nexoskill.evaluation.questionbank.application.service;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.questionbank.application.model.DuplicateQuestionCommand;
import com.nexoskill.evaluation.questionbank.application.model.QuestionDetail;
import com.nexoskill.evaluation.questionbank.application.port.out.QuestionBankPort;
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
        QuestionDetail duplicated = questionBankPort.duplicate(
                command.publicId(), UUID.randomUUID().toString(), command.actorUserId());
        auditLogPort.record(
                command.actorUserId(), "QUESTION_DUPLICATED", "QUESTION_BANK",
                "Se duplicó una pregunta como un nuevo borrador.",
                command.ipAddress(), command.userAgent(),
                Map.of(
                        "sourceQuestionPublicId", command.publicId(),
                        "newQuestionPublicId", duplicated.publicId()
                ), clock.instant());
        return duplicated;
    }
}
