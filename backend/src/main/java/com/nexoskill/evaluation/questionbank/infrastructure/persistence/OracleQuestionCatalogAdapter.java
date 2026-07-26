package com.nexoskill.evaluation.questionbank.infrastructure.persistence;

import com.nexoskill.evaluation.organizations.domain.model.ContentScope;
import com.nexoskill.evaluation.organizations.domain.model.TenantContext;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationRepository;
import com.nexoskill.evaluation.questionbank.application.model.*;
import com.nexoskill.evaluation.questionbank.application.port.out.QuestionCatalogPort;
import com.nexoskill.evaluation.questionbank.domain.model.CatalogStatus;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import com.nexoskill.evaluation.shared.domain.PublicIdNormalizer;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class OracleQuestionCatalogAdapter implements QuestionCatalogPort {
    private final SpringDataQuestionTypeRepository types;
    private final SpringDataQuestionCategoryRepository categories;
    private final SpringDataQuestionRepository questions;
    private final QuestionCategoryStatusHistoryRepository historyRepository;
    private final OrganizationRepository organizationRepository;
    private final Clock clock;

    public OracleQuestionCatalogAdapter(SpringDataQuestionTypeRepository types,
            SpringDataQuestionDifficultyRepository ignoredDifficulties,
            SpringDataQuestionCategoryRepository categories,
            SpringDataQuestionRepository questions,
            QuestionCategoryStatusHistoryRepository historyRepository,
            OrganizationRepository organizationRepository,
            Clock clock) {
        this.types = types;
        this.categories = categories;
        this.questions = questions;
        this.historyRepository = historyRepository;
        this.organizationRepository = organizationRepository;
        this.clock = clock;
    }

    @Override
    public QuestionCatalogs activeCatalogs(TenantContext tenant) {
        return new QuestionCatalogs(
                types.findAllByStatusOrderByNameAsc(CatalogStatus.ACTIVE).stream()
                        .map(type -> new CatalogOption(type.getCode(), type.getName(), type.getDescription())).toList(),
                categories.findVisible(tenant.organizationId(), CatalogStatus.ACTIVE).stream()
                        .map(this::summary).toList());
    }

    @Override
    public List<QuestionCategorySummary> categories(TenantContext tenant, CatalogStatus status) {
        return categories.findVisible(tenant.organizationId(), status).stream().map(this::summary).toList();
    }

    @Override
    public QuestionCategorySummary get(String publicId, TenantContext tenant) {
        return summary(readable(publicId, tenant));
    }

    @Override
    public QuestionCategorySummary create(CategoryCommands.Create command) {
        ScopeTarget target = scope(command.tenant());
        String name = name(command.name());
        String code = code(command.code(), name);
        ensureUnique(code, name, target.scope(), target.organizationId(), null);
        Instant now = clock.instant();
        QuestionCategoryJpaEntity entity;
        try {
            entity = QuestionCategoryJpaEntity.create(UUID.randomUUID().toString(), code, name,
                    nullable(command.description()), target.scope(), target.organizationId(),
                    command.actorUserId(), now);
        } catch (IllegalArgumentException exception) {
            throw error("CATEGORY_TENANT_INVALID", exception.getMessage());
        }
        entity = categories.saveAndFlush(entity);
        historyRepository.save(QuestionCategoryStatusHistoryJpaEntity.create(entity.getId(), command.actorUserId(),
                null, CatalogStatus.ACTIVE, "Creación de categoría", now));
        return summary(entity);
    }

    @Override
    public QuestionCategorySummary update(CategoryCommands.Update command) {
        QuestionCategoryJpaEntity entity = writable(command.publicId(), command.tenant());
        version(entity, command.expectedEntityVersion());
        if (entity.getStatus() == CatalogStatus.DELETED) {
            throw error("CATEGORY_DELETED", "La categoría está eliminada y no puede editarse.");
        }
        String name = name(command.name());
        String code = code(command.code(), name);
        ensureUnique(code, name, entity.getContentScope(), entity.getOwnerOrganizationId(), entity.getPublicId());
        entity.update(code, name, nullable(command.description()), command.actorUserId(), clock.instant());
        return summary(categories.saveAndFlush(entity));
    }

    @Override
    public QuestionCategorySummary changeStatus(CategoryCommands.ChangeStatus command) {
        QuestionCategoryJpaEntity entity = writable(command.publicId(), command.tenant());
        version(entity, command.expectedEntityVersion());
        CatalogStatus previous = entity.getStatus();
        Instant now = clock.instant();
        if (command.status() == CatalogStatus.ACTIVE) {
            if (previous != CatalogStatus.INACTIVE) {
                throw error("CATEGORY_STATUS_TRANSITION_INVALID",
                        "Solamente una categoría inactiva puede reactivarse.");
            }
            entity.activate(command.actorUserId(), now);
        } else if (command.status() == CatalogStatus.INACTIVE) {
            if (previous != CatalogStatus.ACTIVE) {
                throw error("CATEGORY_STATUS_TRANSITION_INVALID",
                        "Solamente una categoría activa puede inactivarse.");
            }
            entity.deactivate(command.actorUserId(), now);
        } else {
            throw error("CATEGORY_STATUS_TRANSITION_INVALID",
                    "La eliminación debe ejecutarse desde la acción administrativa específica.");
        }
        categories.saveAndFlush(entity);
        historyRepository.save(QuestionCategoryStatusHistoryJpaEntity.create(entity.getId(), command.actorUserId(),
                previous, entity.getStatus(), reason(command.reason(), "Cambio de estado"), now));
        return summary(entity);
    }

    @Override
    public QuestionCategorySummary softDelete(CategoryCommands.Delete command) {
        QuestionCategoryJpaEntity entity = writable(command.publicId(), command.tenant());
        version(entity, command.expectedEntityVersion());
        if (entity.getStatus() != CatalogStatus.INACTIVE) {
            throw error("CATEGORY_MUST_BE_INACTIVE",
                    "La categoría debe estar inactiva antes de eliminarse.");
        }
        long dependencies = questions.countAllByCategory(entity.getId());
        if (dependencies > 0) {
            throw error("CATEGORY_IN_USE",
                    "No es posible eliminar la categoría porque está siendo utilizada por preguntas existentes.");
        }
        Instant now = clock.instant();
        entity.softDelete(command.actorUserId(), now);
        categories.saveAndFlush(entity);
        historyRepository.save(QuestionCategoryStatusHistoryJpaEntity.create(entity.getId(), command.actorUserId(),
                CatalogStatus.INACTIVE, CatalogStatus.DELETED,
                reason(command.reason(), "Eliminación lógica de categoría"), now));
        return summary(entity);
    }

    @Override
    public QuestionCategoryDependencies dependencies(String publicId, TenantContext tenant) {
        QuestionCategoryJpaEntity entity = readable(publicId, tenant);
        long count = questions.countAllByCategory(entity.getId());
        boolean canDelete = entity.getStatus() == CatalogStatus.INACTIVE && count == 0;
        String message = count > 0
                ? "No es posible eliminar la categoría porque está siendo utilizada por preguntas existentes."
                : entity.getStatus() != CatalogStatus.INACTIVE
                    ? "La categoría debe estar inactiva antes de eliminarse."
                    : "La categoría no tiene dependencias y puede eliminarse lógicamente.";
        return new QuestionCategoryDependencies(entity.getPublicId(), count, canDelete, message);
    }

    @Override
    public List<QuestionCategoryStatusHistory> history(String publicId, TenantContext tenant) {
        QuestionCategoryJpaEntity entity = readable(publicId, tenant);
        return historyRepository.findAllByCategoryIdOrderByOccurredAtDesc(entity.getId()).stream()
                .map(item -> new QuestionCategoryStatusHistory(item.getId(), item.getPreviousStatus(),
                        item.getNewStatus(), item.getReason(), item.getActorUserId(), item.getOccurredAt()))
                .toList();
    }

    private QuestionCategoryJpaEntity readable(String publicId, TenantContext tenant) {
        QuestionCategoryJpaEntity entity = find(publicId);
        if (entity.getContentScope() == ContentScope.ORGANIZATION
                && !java.util.Objects.equals(entity.getOwnerOrganizationId(), tenant.organizationId())) {
            throw error("CATEGORY_NOT_FOUND", "La categoría solicitada no existe.");
        }
        return entity;
    }

    private QuestionCategoryJpaEntity writable(String publicId, TenantContext tenant) {
        QuestionCategoryJpaEntity entity = readable(publicId, tenant);
        if (entity.getContentScope() == ContentScope.GLOBAL && !tenant.globalAdministrator()) {
            throw error("CATEGORY_GLOBAL_IMMUTABLE",
                    "Las categorías globales solamente pueden administrarse desde el contexto global.");
        }
        if (entity.getContentScope() == ContentScope.GLOBAL && !tenant.globalScope()) {
            throw error("CATEGORY_GLOBAL_CONTEXT_REQUIRED",
                    "Selecciona el contexto GLOBAL para administrar una categoría global.");
        }
        return entity;
    }

    private QuestionCategoryJpaEntity find(String publicId) {
        String id = PublicIdNormalizer.requiredUuid(publicId, "CATEGORY_ID_INVALID",
                "La categoría indicada no es válida.");
        return categories.findByPublicId(id)
                .orElseThrow(() -> error("CATEGORY_NOT_FOUND", "La categoría solicitada no existe."));
    }

    private ScopeTarget scope(TenantContext tenant) {
        if (tenant.globalScope()) return new ScopeTarget(ContentScope.GLOBAL, null);
        if (!tenant.hasOrganization()) {
            throw error("TENANT_NOT_RESOLVED", "No fue posible determinar la organización para completar la operación.");
        }
        return new ScopeTarget(ContentScope.ORGANIZATION, tenant.organizationId());
    }

    private void ensureUnique(String code, String name, ContentScope scope, Long organizationId, String ignore) {
        for (QuestionCategoryJpaEntity entity : categories.findWithinScope(scope, organizationId)) {
            if (ignore != null && entity.getPublicId().equals(ignore)) continue;
            if (entity.getCode().equalsIgnoreCase(code)) {
                throw error("CATEGORY_CODE_ALREADY_EXISTS",
                        "Ya existe una categoría con ese código dentro del mismo alcance.");
            }
            if (normalize(entity.getName()).equals(normalize(name))) {
                throw error("CATEGORY_ALREADY_EXISTS",
                        "Ya existe una categoría con ese nombre dentro del mismo alcance.");
            }
        }
    }

    private QuestionCategorySummary summary(QuestionCategoryJpaEntity entity) {
        String ownerPublicId = null;
        String ownerName = null;
        if (entity.getOwnerOrganizationId() != null) {
            var organization = organizationRepository.findById(entity.getOwnerOrganizationId()).orElse(null);
            if (organization != null) {
                ownerPublicId = organization.getPublicId();
                ownerName = organization.getName();
            }
        }
        return new QuestionCategorySummary(entity.getPublicId(), entity.getCode(), entity.getName(),
                entity.getDescription(), entity.getStatus(), entity.getContentScope(), ownerPublicId, ownerName,
                entity.getVersion(), questions.countAllByCategory(entity.getId()), entity.getCreatedBy(),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private void version(QuestionCategoryJpaEntity entity, long expected) {
        if (entity.getVersion() != expected) {
            throw error("CATEGORY_CONCURRENT_MODIFICATION",
                    "La categoría fue modificada por otra persona. Recarga la información.");
        }
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

    private String reason(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private BusinessException error(String code, String message) { return new BusinessException(code, message); }

    private record ScopeTarget(ContentScope scope, Long organizationId) {}
}
