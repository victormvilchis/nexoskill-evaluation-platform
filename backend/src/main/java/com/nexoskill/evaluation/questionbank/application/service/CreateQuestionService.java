package com.nexoskill.evaluation.questionbank.application.service;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.questionbank.application.model.CreateQuestionCommand;
import com.nexoskill.evaluation.questionbank.application.model.QuestionDetail;
import com.nexoskill.evaluation.questionbank.application.model.QuestionOptionCommand;
import com.nexoskill.evaluation.questionbank.application.port.out.QuestionBankPort;
import com.nexoskill.evaluation.questionbank.domain.model.QuestionTypeCode;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateQuestionService {

    private final QuestionBankPort questionBankPort;
    private final QuestionDraftValidator validator;
    private final AuditLogPort auditLogPort;
    private final Clock clock;

    public CreateQuestionService(
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
    public QuestionDetail create(CreateQuestionCommand command) {
        QuestionTypeCode type = parseType(command.typeCode());
        String difficulty = requiredCode(
                command.difficultyCode(),
                "QUESTION_DIFFICULTY_REQUIRED",
                "La dificultad es obligatoria."
        );
        String categoryPublicId = requiredCode(
                command.categoryPublicId(),
                "QUESTION_CATEGORY_REQUIRED",
                "La categoría es obligatoria."
        );
        String statement = command.statement() == null
                ? null
                : command.statement().trim();
        String explanation = normalizeExplanation(command.explanation());
        List<QuestionOptionCommand> options = normalizeOptions(command.options());

        validator.validate(type, statement, options);

        QuestionDetail created = questionBankPort.create(
                new QuestionBankPort.NewQuestionData(
                        UUID.randomUUID().toString(),
                        type.name(),
                        difficulty,
                        categoryPublicId,
                        statement,
                        explanation,
                        options,
                        command.actorUserId()
                )
        );

        auditLogPort.record(
                command.actorUserId(),
                "QUESTION_CREATED",
                "QUESTION_BANK",
                "Se creó una pregunta en estado borrador.",
                command.ipAddress(),
                command.userAgent(),
                Map.of(
                        "questionPublicId", created.publicId(),
                        "type", created.typeCode(),
                        "difficulty", created.difficultyCode(),
                        "category", created.categoryName(),
                        "status", created.status().name()
                ),
                clock.instant()
        );

        return created;
    }

    private QuestionTypeCode parseType(String value) {
        try {
            return QuestionTypeCode.valueOf(requiredCode(
                    value,
                    "QUESTION_TYPE_REQUIRED",
                    "El tipo de pregunta es obligatorio."
            ));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    "QUESTION_TYPE_INVALID",
                    "El tipo de pregunta indicado no es válido."
            );
        }
    }

    private String requiredCode(String value, String code, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(code, message);
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeExplanation(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String explanation = value.trim();
        if (explanation.length() > 10000) {
            throw new BusinessException(
                    "QUESTION_EXPLANATION_TOO_LONG",
                    "La explicación no puede superar 10,000 caracteres."
            );
        }
        return explanation;
    }

    private List<QuestionOptionCommand> normalizeOptions(
            List<QuestionOptionCommand> options) {
        return options.stream()
                .map(option -> new QuestionOptionCommand(
                        option.text() == null ? null : option.text().trim(),
                        option.correct()
                ))
                .toList();
    }
}
