package com.nexoskill.evaluation.questionbank.application.service;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.questionbank.application.model.CreateQuestionCategoryCommand;
import com.nexoskill.evaluation.questionbank.application.model.QuestionCategorySummary;
import com.nexoskill.evaluation.questionbank.application.port.out.QuestionCatalogPort;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import java.text.Normalizer;
import java.time.Clock;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateQuestionCategoryService {

    private final QuestionCatalogPort catalogPort;
    private final AuditLogPort auditLogPort;
    private final Clock clock;

    public CreateQuestionCategoryService(
            QuestionCatalogPort catalogPort,
            AuditLogPort auditLogPort,
            Clock clock) {
        this.catalogPort = catalogPort;
        this.auditLogPort = auditLogPort;
        this.clock = clock;
    }

    @Transactional
    public QuestionCategorySummary create(CreateQuestionCategoryCommand command) {
        String code = normalizeCode(command.code(), command.name());
        String name = requireName(command.name());
        String description = normalizeOptional(command.description());

        if (catalogPort.categoryCodeExists(code)) {
            throw new BusinessException(
                    "QUESTION_CATEGORY_CODE_EXISTS",
                    "Ya existe una categoría con ese código."
            );
        }
        if (catalogPort.categoryNameExists(name.toLowerCase(Locale.ROOT))) {
            throw new BusinessException(
                    "QUESTION_CATEGORY_NAME_EXISTS",
                    "Ya existe una categoría con ese nombre."
            );
        }

        QuestionCategorySummary created = catalogPort.createCategory(
                new QuestionCatalogPort.NewCategoryData(
                        UUID.randomUUID().toString(),
                        code,
                        name,
                        description,
                        command.actorUserId()
                )
        );

        auditLogPort.record(
                command.actorUserId(),
                "QUESTION_CATEGORY_CREATED",
                "QUESTION_BANK",
                "Se creó una categoría para el banco de preguntas.",
                command.ipAddress(),
                command.userAgent(),
                Map.of(
                        "categoryPublicId", created.publicId(),
                        "categoryCode", created.code(),
                        "categoryName", created.name()
                ),
                clock.instant()
        );

        return created;
    }

    private String requireName(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(
                    "QUESTION_CATEGORY_NAME_REQUIRED",
                    "El nombre de la categoría es obligatorio."
            );
        }
        String name = value.trim();
        if (name.length() > 150) {
            throw new BusinessException(
                    "QUESTION_CATEGORY_NAME_TOO_LONG",
                    "El nombre de la categoría no puede superar 150 caracteres."
            );
        }
        return name;
    }

    private String normalizeCode(String code, String name) {
        String source = code == null || code.isBlank() ? name : code;
        if (source == null || source.isBlank()) {
            throw new BusinessException(
                    "QUESTION_CATEGORY_CODE_REQUIRED",
                    "El código de la categoría es obligatorio."
            );
        }
        String normalized = Normalizer.normalize(source.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (normalized.isBlank() || normalized.length() > 80) {
            throw new BusinessException(
                    "QUESTION_CATEGORY_CODE_INVALID",
                    "El código de la categoría no es válido."
            );
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > 500) {
            throw new BusinessException(
                    "QUESTION_CATEGORY_DESCRIPTION_TOO_LONG",
                    "La descripción no puede superar 500 caracteres."
            );
        }
        return normalized;
    }
}
