package com.nexoskill.evaluation.questionbank.application.service;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.questionbank.application.model.QuestionDetail;
import com.nexoskill.evaluation.questionbank.application.model.QuestionOptionCommand;
import com.nexoskill.evaluation.questionbank.application.model.UpdateQuestionCommand;
import com.nexoskill.evaluation.questionbank.application.port.out.QuestionBankPort;
import com.nexoskill.evaluation.questionbank.domain.model.QuestionTypeCode;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import com.nexoskill.evaluation.shared.domain.PublicIdNormalizer;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateQuestionService {

    private final QuestionBankPort questionBankPort;
    private final QuestionDraftValidator validator;
    private final AuditLogPort auditLogPort;
    private final Clock clock;

    public UpdateQuestionService(
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
    public QuestionDetail update(UpdateQuestionCommand command) {
        String questionPublicId = PublicIdNormalizer.requiredUuid(
                command.publicId(), "QUESTION_ID_REQUIRED", "La pregunta es obligatoria."
        );
        QuestionTypeCode type = parseType(command.typeCode());
        String difficulty = requiredCode(command.difficultyCode(),
                "QUESTION_DIFFICULTY_REQUIRED", "La dificultad es obligatoria.");
        String category = PublicIdNormalizer.requiredUuid(
                command.categoryPublicId(),
                "QUESTION_CATEGORY_REQUIRED",
                "La categoría es obligatoria.");
        String statement = command.statement() == null ? null : command.statement().trim();
        String explanation = normalizeExplanation(command.explanation());
        String changeSummary = normalizeChangeSummary(command.changeSummary());
        List<QuestionOptionCommand> options = normalizeOptions(command.options());
        validator.validate(type, statement, options);

        QuestionBankPort.UpdateResult result = questionBankPort.update(
                new QuestionBankPort.UpdateQuestionData(
                        questionPublicId, type.name(), difficulty, category,
                        statement, explanation, changeSummary, options,
                        command.expectedEntityVersion(), command.actorUserId()
                )
        );

        auditLogPort.record(
                command.actorUserId(),
                result.createdNewVersion() ? "QUESTION_VERSION_CREATED" : "QUESTION_UPDATED",
                "QUESTION_BANK",
                result.createdNewVersion()
                        ? "Se creó una nueva versión en borrador de la pregunta."
                        : "Se actualizó la pregunta en borrador.",
                command.ipAddress(),
                command.userAgent(),
                Map.of(
                        "questionPublicId", result.question().publicId(),
                        "previousVersion", result.previousVersionNumber(),
                        "currentVersion", result.question().versionNumber(),
                        "createdNewVersion", result.createdNewVersion(),
                        "status", result.question().status().name()
                ),
                clock.instant()
        );
        return result.question();
    }

    private QuestionTypeCode parseType(String value) {
        try {
            return QuestionTypeCode.valueOf(requiredCode(value,
                    "QUESTION_TYPE_REQUIRED", "El tipo de pregunta es obligatorio."));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("QUESTION_TYPE_INVALID",
                    "El tipo de pregunta indicado no es válido.");
        }
    }

    private String requiredCode(String value, String code, String message) {
        if (value == null || value.isBlank()) throw new BusinessException(code, message);
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeExplanation(String value) {
        if (value == null || value.isBlank()) return null;
        String result = value.trim();
        if (result.length() > 10000) throw new BusinessException(
                "QUESTION_EXPLANATION_TOO_LONG",
                "La explicación no puede superar 10,000 caracteres.");
        return result;
    }

    private String normalizeChangeSummary(String value) {
        if (value == null || value.isBlank()) return null;
        String result = value.trim();
        if (result.length() > 500) throw new BusinessException(
                "QUESTION_CHANGE_SUMMARY_TOO_LONG",
                "El resumen del cambio no puede superar 500 caracteres.");
        return result;
    }

    private List<QuestionOptionCommand> normalizeOptions(List<QuestionOptionCommand> options) {
        return options.stream().map(option -> new QuestionOptionCommand(
                option.text() == null ? null : option.text().trim(), option.correct())).toList();
    }
}
