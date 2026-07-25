package com.nexoskill.evaluation.questionbank.infrastructure.persistence;

import com.nexoskill.evaluation.questionbank.application.model.*;
import com.nexoskill.evaluation.questionbank.application.port.out.QuestionCatalogPort;
import com.nexoskill.evaluation.questionbank.domain.model.CatalogStatus;
import com.nexoskill.evaluation.shared.domain.*;
import java.text.Normalizer;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class OracleQuestionCatalogAdapter implements QuestionCatalogPort {
    private final SpringDataQuestionTypeRepository types;
    private final SpringDataQuestionCategoryRepository categories;
    private final SpringDataQuestionRepository questions;
    private final Clock clock;

    public OracleQuestionCatalogAdapter(SpringDataQuestionTypeRepository types,
            SpringDataQuestionDifficultyRepository ignoredDifficulties,
            SpringDataQuestionCategoryRepository categories,
            SpringDataQuestionRepository questions,
            Clock clock) {
        this.types = types;
        this.categories = categories;
        this.questions = questions;
        this.clock = clock;
    }

    public QuestionCatalogs activeCatalogs() {
        return new QuestionCatalogs(
                types.findAllByStatusOrderByNameAsc(CatalogStatus.ACTIVE).stream()
                        .map(type -> new CatalogOption(type.getCode(), type.getName(), type.getDescription())).toList(),
                categories.findAllByStatusOrderByNameAsc(CatalogStatus.ACTIVE).stream().map(this::summary).toList());
    }

    public List<QuestionCategorySummary> categories() {
        return categories.findAllByOrderByNameAsc().stream().map(this::summary).toList();
    }

    public QuestionCategorySummary create(CategoryCommands.Create command) {
        String name = name(command.name());
        String code = code(command.code(), name);
        ensureUnique(code, name, null);
        return summary(categories.saveAndFlush(QuestionCategoryJpaEntity.create(UUID.randomUUID().toString(), code,
                name, nullable(command.description()), command.actorUserId(), clock.instant())));
    }

    public QuestionCategorySummary update(CategoryCommands.Update command) {
        String id = PublicIdNormalizer.requiredUuid(command.publicId(), "CATEGORY_ID_INVALID",
                "La categoría indicada no es válida.");
        var entity = categories.findByPublicId(id)
                .orElseThrow(() -> error("CATEGORY_NOT_FOUND", "La categoría solicitada no existe."));
        if (entity.getVersion() != command.expectedEntityVersion()) {
            throw error("CATEGORY_CONCURRENT_MODIFICATION", "La categoría fue modificada por otra persona.");
        }
        String name = name(command.name());
        String code = code(command.code(), name);
        ensureUnique(code, name, id);
        entity.update(code, name, nullable(command.description()), command.actorUserId(), clock.instant());
        return summary(categories.saveAndFlush(entity));
    }

    public QuestionCategorySummary changeStatus(CategoryCommands.ChangeStatus command) {
        String id = PublicIdNormalizer.requiredUuid(command.publicId(), "CATEGORY_ID_INVALID",
                "La categoría indicada no es válida.");
        var entity = categories.findByPublicId(id)
                .orElseThrow(() -> error("CATEGORY_NOT_FOUND", "La categoría solicitada no existe."));
        if (entity.getVersion() != command.expectedEntityVersion()) {
            throw error("CATEGORY_CONCURRENT_MODIFICATION",
                    "La categoría fue modificada por otra persona. Recarga la información.");
        }
        entity.changeStatus(command.status(), command.actorUserId(), clock.instant());
        return summary(categories.saveAndFlush(entity));
    }

    private void ensureUnique(String code, String name, String ignore) {
        for (var entity : categories.findAll()) {
            if (ignore != null && entity.getPublicId().equals(ignore)) continue;
            if (entity.getCode().equalsIgnoreCase(code)) {
                throw error("CATEGORY_CODE_ALREADY_EXISTS", "Ya existe una categoría con ese código.");
            }
            if (normalize(entity.getName()).equals(normalize(name))) {
                throw error("CATEGORY_ALREADY_EXISTS", "Ya existe una categoría con ese nombre.");
            }
        }
    }

    private QuestionCategorySummary summary(QuestionCategoryJpaEntity entity) {
        return new QuestionCategorySummary(entity.getPublicId(), entity.getCode(), entity.getName(),
                entity.getDescription(), entity.getStatus(), entity.getVersion(),
                questions.countActiveByCategory(entity.getId()), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private String name(String value) {
        if (value == null || value.isBlank()) throw error("CATEGORY_NAME_REQUIRED", "El nombre es obligatorio.");
        String normalized = value.trim().replaceAll("\\s+", " ");
        if (normalized.length() > 150) {
            throw error("CATEGORY_NAME_TOO_LONG", "El nombre no puede superar 150 caracteres.");
        }
        return normalized;
    }

    private String code(String value, String name) {
        String source = value == null || value.isBlank() ? name : value;
        String normalized = Normalizer.normalize(source, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (normalized.isBlank()) throw error("CATEGORY_CODE_INVALID", "No fue posible generar un código válido.");
        return normalized.length() > 80 ? normalized.substring(0, 80) : normalized;
    }

    private String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC).replaceAll("\\s+", " ").trim()
                .toLowerCase(Locale.ROOT);
    }

    private String nullable(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private BusinessException error(String code, String message) { return new BusinessException(code, message); }
}
