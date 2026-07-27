package com.nexoskill.evaluation.catalogs.application;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.catalogs.application.CatalogModels.*;
import com.nexoskill.evaluation.catalogs.domain.CatalogType;
import com.nexoskill.evaluation.certifications.infrastructure.persistence.*;
import com.nexoskill.evaluation.organizations.domain.model.ContentScope;
import com.nexoskill.evaluation.organizations.domain.model.TenantContext;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationJpaEntity;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationRepository;
import com.nexoskill.evaluation.questionbank.domain.model.CatalogStatus;
import com.nexoskill.evaluation.questionbank.domain.model.QuestionTechnologyStatus;
import com.nexoskill.evaluation.questionbank.infrastructure.persistence.*;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogAdministrationService {
    private static final String ACTIVE = "ACTIVE";
    private static final String INACTIVE = "INACTIVE";

    private final SpringDataQuestionCategoryRepository categories;
    private final SpringDataQuestionTechnologyRepository technologies;
    private final SpringDataQuestionTypeRepository types;
    private final SpringDataQuestionDifficultyRepository difficulties;
    private final ProfessionalCertificationProfileRepository profiles;
    private final CertificationTechnologyRepository certificationTechnologies;
    private final TechnologicalProfileCatalogRepository technologicalProfiles;
    private final OrganizationRepository organizations;
    private final NamedParameterJdbcTemplate jdbc;
    private final AuditLogPort audit;
    private final Clock clock;

    public CatalogAdministrationService(SpringDataQuestionCategoryRepository categories,
            SpringDataQuestionTechnologyRepository technologies,
            SpringDataQuestionTypeRepository types,
            SpringDataQuestionDifficultyRepository difficulties,
            ProfessionalCertificationProfileRepository profiles,
            CertificationTechnologyRepository certificationTechnologies,
            TechnologicalProfileCatalogRepository technologicalProfiles,
            OrganizationRepository organizations,
            NamedParameterJdbcTemplate jdbc,
            AuditLogPort audit,
            Clock clock) {
        this.categories = categories;
        this.technologies = technologies;
        this.types = types;
        this.difficulties = difficulties;
        this.profiles = profiles;
        this.certificationTechnologies = certificationTechnologies;
        this.technologicalProfiles = technologicalProfiles;
        this.organizations = organizations;
        this.jdbc = jdbc;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<TypeSummary> types(TenantContext tenant) {
        requireGlobalAdministrator(tenant);
        return Arrays.stream(CatalogType.values()).map(this::typeSummary).toList();
    }

    @Transactional(readOnly = true)
    public List<Item> items(CatalogType type, String status, String organizationPublicId,
            TenantContext tenant) {
        requireGlobalAdministrator(tenant);
        String normalizedStatus = parseStatus(status);
        return switch (type) {
            case CATEGORIES -> categoryItems(normalizedStatus, organizationPublicId);
            case TECHNOLOGIES -> technologies.findAllByOrderByDisplayOrderAscNameAsc().stream()
                    .filter(value -> matchesStatus(value.getStatus().name(), normalizedStatus))
                    .map(this::technologyItem).toList();
            case PROFESSIONAL_PROFILES -> profiles.findAllByOrderBySortOrderAscNameAsc().stream()
                    .filter(value -> matchesStatus(value.getStatus(), normalizedStatus))
                    .map(this::profileItem).toList();
            case TECHNOLOGICAL_PROFILES -> technologicalProfiles.findAll().stream()
                    .filter(value -> matchesStatus(value.getStatus(), normalizedStatus))
                    .sorted(Comparator.comparingInt(TechnologicalProfileCatalogJpaEntity::getDisplayOrder)
                            .thenComparing(TechnologicalProfileCatalogJpaEntity::getName))
                    .map(this::technologicalProfileItem).toList();
            case QUESTION_TYPES -> types.findAllByOrderByDisplayOrderAscNameAsc().stream()
                    .filter(value -> value.getStatus() != CatalogStatus.DELETED)
                    .filter(value -> matchesStatus(value.getStatus().name(), normalizedStatus))
                    .map(this::typeItem).toList();
            case DIFFICULTIES -> difficulties.findAllByOrderBySortOrderAsc().stream()
                    .filter(value -> value.getStatus() != CatalogStatus.DELETED)
                    .filter(value -> matchesStatus(value.getStatus().name(), normalizedStatus))
                    .map(this::difficultyItem).toList();
        };
    }

    @Transactional(readOnly = true)
    public Item get(CatalogType type, String id, TenantContext tenant) {
        requireGlobalAdministrator(tenant);
        return switch (type) {
            case CATEGORIES -> categoryItem(requireCategory(id));
            case TECHNOLOGIES -> technologyItem(requireTechnology(id));
            case PROFESSIONAL_PROFILES -> profileItem(requireProfile(id));
            case TECHNOLOGICAL_PROFILES -> technologicalProfileItem(requireTechnologicalProfile(id));
            case QUESTION_TYPES -> typeItem(requireType(id));
            case DIFFICULTIES -> difficultyItem(requireDifficulty(id));
        };
    }

    @Transactional
    public Item create(CatalogType type, Upsert request, Long actorId, TenantContext tenant) {
        requireGlobalAdministrator(tenant);
        validate(request);
        String code = normalizeCode(request.code());
        String name = clean(request.name(), 200);
        assertUnique(type, null, code, name, request.organizationPublicId());
        Instant now = clock.instant();
        Item result = switch (type) {
            case CATEGORIES -> createCategory(request, code, name, actorId, now);
            case TECHNOLOGIES -> {
                QuestionTechnologyJpaEntity entity = technologies.saveAndFlush(QuestionTechnologyJpaEntity.create(
                        UUID.randomUUID().toString(), code, name, cleanOptional(request.description(), 500),
                        order(request.displayOrder()), actorId, now));
                synchronizeCertificationTechnology(entity, now);
                yield technologyItem(entity);
            }
            case PROFESSIONAL_PROFILES -> profileItem(profiles.saveAndFlush(
                    ProfessionalCertificationProfileJpaEntity.create(UUID.randomUUID().toString(), code, name,
                            cleanOptional(request.description(), 500), order(request.displayOrder()),
                            parseSuggestedProfile(request.suggestedTechnologicalProfile()), actorId, now)));
            case TECHNOLOGICAL_PROFILES -> technologicalProfileItem(technologicalProfiles.saveAndFlush(
                    TechnologicalProfileCatalogJpaEntity.create(UUID.randomUUID().toString(), code, name,
                            cleanOptional(request.description(), 500), order(request.displayOrder()), actorId, now)));
            case QUESTION_TYPES -> typeItem(types.saveAndFlush(QuestionTypeJpaEntity.create(code, name,
                    cleanOptional(request.description(), 500), order(request.displayOrder()), actorId, now)));
            case DIFFICULTIES -> difficultyItem(difficulties.saveAndFlush(QuestionDifficultyJpaEntity.create(code,
                    name, cleanOptional(request.description(), 500), order(request.displayOrder()), actorId, now)));
        };
        record(actorId, "CATALOG_ITEM_CREATED", type, result, "Se creó un valor de catálogo.");
        return result;
    }

    @Transactional
    public Item update(CatalogType type, String id, Upsert request, Long actorId, TenantContext tenant) {
        requireGlobalAdministrator(tenant);
        validate(request);
        String name = clean(request.name(), 200);
        String currentCode = currentCode(type, id);
        if (request.code() != null && !normalizeCode(request.code()).equals(currentCode)) {
            throw new BusinessException("CATALOG_CODE_IMMUTABLE",
                    "El código es estable y no puede modificarse después de crear el registro.");
        }
        assertUnique(type, id, currentCode, name, getWithoutSecurity(type, id).organizationPublicId());
        Instant now = clock.instant();
        Item result = switch (type) {
            case CATEGORIES -> {
                QuestionCategoryJpaEntity entity = requireCategory(id);
                checkVersion(entity.getVersion(), request.expectedVersion());
                entity.update(entity.getCode(), name, cleanOptional(request.description(), 500), actorId, now);
                yield categoryItem(categories.saveAndFlush(entity));
            }
            case TECHNOLOGIES -> {
                QuestionTechnologyJpaEntity entity = requireTechnology(id);
                checkVersion(entity.getVersion(), request.expectedVersion());
                entity.update(name, cleanOptional(request.description(), 500), order(request.displayOrder()), actorId, now);
                entity = technologies.saveAndFlush(entity);
                synchronizeCertificationTechnology(entity, now);
                yield technologyItem(entity);
            }
            case PROFESSIONAL_PROFILES -> {
                ProfessionalCertificationProfileJpaEntity entity = requireProfile(id);
                checkVersion(entity.getVersion(), request.expectedVersion());
                entity.update(name, cleanOptional(request.description(), 500), order(request.displayOrder()),
                        parseSuggestedProfile(request.suggestedTechnologicalProfile()), actorId, now);
                yield profileItem(profiles.saveAndFlush(entity));
            }
            case TECHNOLOGICAL_PROFILES -> {
                TechnologicalProfileCatalogJpaEntity entity = requireTechnologicalProfile(id);
                checkVersion(entity.getVersion(), request.expectedVersion());
                entity.update(name, cleanOptional(request.description(), 500), order(request.displayOrder()), actorId, now);
                yield technologicalProfileItem(technologicalProfiles.saveAndFlush(entity));
            }
            case QUESTION_TYPES -> {
                QuestionTypeJpaEntity entity = requireType(id);
                checkVersion(entity.getVersion(), request.expectedVersion());
                entity.update(name, cleanOptional(request.description(), 500), order(request.displayOrder()), actorId, now);
                yield typeItem(types.saveAndFlush(entity));
            }
            case DIFFICULTIES -> {
                QuestionDifficultyJpaEntity entity = requireDifficulty(id);
                checkVersion(entity.getVersion(), request.expectedVersion());
                entity.update(name, cleanOptional(request.description(), 500), order(request.displayOrder()), actorId, now);
                yield difficultyItem(difficulties.saveAndFlush(entity));
            }
        };
        record(actorId, "CATALOG_ITEM_UPDATED", type, result, "Se actualizó un valor de catálogo.");
        return result;
    }

    @Transactional
    public Item changeStatus(CatalogType type, String id, String nextStatus, Long expectedVersion,
            Long actorId, TenantContext tenant) {
        requireGlobalAdministrator(tenant);
        String target = requireOperationalStatus(nextStatus);
        Instant now = clock.instant();
        Item result = switch (type) {
            case CATEGORIES -> {
                QuestionCategoryJpaEntity entity = requireCategory(id);
                checkVersion(entity.getVersion(), expectedVersion);
                if (target.equals(ACTIVE)) entity.activate(actorId, now); else entity.deactivate(actorId, now);
                yield categoryItem(categories.saveAndFlush(entity));
            }
            case TECHNOLOGIES -> {
                QuestionTechnologyJpaEntity entity = requireTechnology(id);
                checkVersion(entity.getVersion(), expectedVersion);
                entity.changeStatus(QuestionTechnologyStatus.valueOf(target), actorId, now);
                entity = technologies.saveAndFlush(entity);
                synchronizeCertificationTechnology(entity, now);
                yield technologyItem(entity);
            }
            case PROFESSIONAL_PROFILES -> {
                ProfessionalCertificationProfileJpaEntity entity = requireProfile(id);
                checkVersion(entity.getVersion(), expectedVersion);
                entity.changeStatus(target, actorId, now);
                yield profileItem(profiles.saveAndFlush(entity));
            }
            case TECHNOLOGICAL_PROFILES -> {
                TechnologicalProfileCatalogJpaEntity entity = requireTechnologicalProfile(id);
                checkVersion(entity.getVersion(), expectedVersion);
                entity.changeStatus(target, actorId, now);
                yield technologicalProfileItem(technologicalProfiles.saveAndFlush(entity));
            }
            case QUESTION_TYPES -> {
                QuestionTypeJpaEntity entity = requireType(id);
                checkVersion(entity.getVersion(), expectedVersion);
                entity.changeStatus(CatalogStatus.valueOf(target), actorId, now);
                yield typeItem(types.saveAndFlush(entity));
            }
            case DIFFICULTIES -> {
                QuestionDifficultyJpaEntity entity = requireDifficulty(id);
                checkVersion(entity.getVersion(), expectedVersion);
                entity.changeStatus(CatalogStatus.valueOf(target), actorId, now);
                yield difficultyItem(difficulties.saveAndFlush(entity));
            }
        };
        record(actorId, target.equals(ACTIVE) ? "CATALOG_ITEM_ACTIVATED" : "CATALOG_ITEM_DEACTIVATED",
                type, result, "Se cambió el estado del valor de catálogo.");
        return result;
    }

    @Transactional(readOnly = true)
    public Dependencies dependencies(CatalogType type, String id, TenantContext tenant) {
        requireGlobalAdministrator(tenant);
        Item item = get(type, id, tenant);
        long count = dependencyCount(type, dependencyKey(type, id));
        List<String> details = count == 0 ? List.of() : List.of(dependencyLabel(type, count));
        return new Dependencies(count, details, INACTIVE.equals(item.status()) && count == 0);
    }

    @Transactional
    public void delete(CatalogType type, String id, Long expectedVersion, Long actorId, TenantContext tenant) {
        requireGlobalAdministrator(tenant);
        Item item = get(type, id, tenant);
        if (!INACTIVE.equals(item.status())) {
            throw new BusinessException("CATALOG_MUST_BE_INACTIVE",
                    "Primero debes inactivar el registro antes de eliminarlo.");
        }
        checkVersion(item.version(), expectedVersion);
        long count = dependencyCount(type, dependencyKey(type, id));
        if (count > 0) {
            throw new BusinessException("CATALOG_IN_USE",
                    "No es posible eliminar este registro porque está siendo utilizado por información existente. Puedes mantenerlo inactivo para impedir su uso en nuevos registros.");
        }
        switch (type) {
            case CATEGORIES -> {
                QuestionCategoryJpaEntity entity = requireCategory(id);
                jdbc.update("DELETE FROM QUESTION_CATEGORY_STATUS_HISTORY WHERE CATEGORY_ID = :id",
                        new MapSqlParameterSource("id", entity.getId()));
                categories.delete(entity);
                categories.flush();
            }
            case TECHNOLOGIES -> {
                QuestionTechnologyJpaEntity entity = requireTechnology(id);
                certificationTechnologies.findByMasterTechnologyId(entity.getId())
                        .ifPresent(certificationTechnologies::delete);
                certificationTechnologies.flush();
                technologies.delete(entity);
                technologies.flush();
            }
            case PROFESSIONAL_PROFILES -> { profiles.delete(requireProfile(id)); profiles.flush(); }
            case TECHNOLOGICAL_PROFILES -> { technologicalProfiles.delete(requireTechnologicalProfile(id)); technologicalProfiles.flush(); }
            case QUESTION_TYPES -> { types.delete(requireType(id)); types.flush(); }
            case DIFFICULTIES -> { difficulties.delete(requireDifficulty(id)); difficulties.flush(); }
        }
        record(actorId, "CATALOG_ITEM_DELETED", type, item,
                "Se eliminó físicamente un valor inactivo sin dependencias.");
    }

    private TypeSummary typeSummary(CatalogType type) {
        List<Item> values = itemsWithoutSecurity(type);
        return new TypeSummary(type, type.label(), type.description(),
                values.stream().filter(value -> ACTIVE.equals(value.status())).count(),
                values.stream().filter(value -> INACTIVE.equals(value.status())).count(),
                values.stream().map(Item::updatedAt).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null),
                type.tenantAware());
    }

    private List<Item> itemsWithoutSecurity(CatalogType type) {
        return switch (type) {
            case CATEGORIES -> categories.findAll().stream().filter(value -> value.getStatus() != CatalogStatus.DELETED)
                    .map(this::categoryItem).toList();
            case TECHNOLOGIES -> technologies.findAllByOrderByDisplayOrderAscNameAsc().stream().map(this::technologyItem).toList();
            case PROFESSIONAL_PROFILES -> profiles.findAllByOrderBySortOrderAscNameAsc().stream().map(this::profileItem).toList();
            case TECHNOLOGICAL_PROFILES -> technologicalProfiles.findAll().stream().map(this::technologicalProfileItem).toList();
            case QUESTION_TYPES -> types.findAllByOrderByDisplayOrderAscNameAsc().stream()
                    .filter(value -> value.getStatus() != CatalogStatus.DELETED).map(this::typeItem).toList();
            case DIFFICULTIES -> difficulties.findAllByOrderBySortOrderAsc().stream()
                    .filter(value -> value.getStatus() != CatalogStatus.DELETED).map(this::difficultyItem).toList();
        };
    }

    private List<Item> categoryItems(String status, String organizationPublicId) {
        Long organizationId = null;
        if (organizationPublicId != null && !organizationPublicId.isBlank()) {
            organizationId = organizations.findByPublicId(organizationPublicId.trim())
                    .orElseThrow(() -> new BusinessException("ORGANIZATION_NOT_FOUND", "La organización solicitada no existe."))
                    .getId();
        }
        Long selectedOrganizationId = organizationId;
        return categories.findAll().stream()
                .filter(value -> value.getStatus() != CatalogStatus.DELETED)
                .filter(value -> matchesStatus(value.getStatus().name(), status))
                .filter(value -> selectedOrganizationId == null || Objects.equals(value.getOwnerOrganizationId(), selectedOrganizationId))
                .sorted(Comparator.comparing(QuestionCategoryJpaEntity::getName))
                .map(this::categoryItem).toList();
    }

    private Item createCategory(Upsert request, String code, String name, Long actorId, Instant now) {
        OrganizationJpaEntity owner = request.organizationPublicId() == null || request.organizationPublicId().isBlank()
                ? organizations.findByCode(OrganizationJpaEntity.GLOBAL_CODE)
                    .orElseThrow(() -> new BusinessException("GLOBAL_ORGANIZATION_NOT_FOUND", "La organización GLOBAL no está configurada."))
                : organizations.findByPublicId(request.organizationPublicId().trim())
                    .orElseThrow(() -> new BusinessException("ORGANIZATION_NOT_FOUND", "La organización solicitada no existe."));
        ContentScope scope = owner.isGlobal() ? ContentScope.GLOBAL : ContentScope.ORGANIZATION;
        QuestionCategoryJpaEntity entity = QuestionCategoryJpaEntity.create(UUID.randomUUID().toString(), code,
                name, cleanOptional(request.description(), 500), scope, owner.getId(), actorId, now);
        return categoryItem(categories.saveAndFlush(entity));
    }

    private Item categoryItem(QuestionCategoryJpaEntity value) {
        OrganizationJpaEntity owner = organizations.findById(value.getOwnerOrganizationId()).orElse(null);
        return new Item(value.getPublicId(), value.getCode(), value.getName(), value.getDescription(),
                value.getStatus().name(), 0, value.getContentScope().name(),
                owner == null ? null : owner.getPublicId(), owner == null ? null : owner.getName(), null,
                value.getCreatedBy(), value.getUpdatedBy(), value.getCreatedAt(), value.getUpdatedAt(),
                value.getVersion(), dependencyCount(CatalogType.CATEGORIES, value.getId()));
    }

    private Item technologyItem(QuestionTechnologyJpaEntity value) {
        return new Item(value.getPublicId(), value.getCode(), value.getName(), value.getDescription(),
                value.getStatus().name(), value.getDisplayOrder(), "GLOBAL", null, "GLOBAL", null,
                value.getCreatedBy(), value.getUpdatedBy(), value.getCreatedAt(), value.getUpdatedAt(),
                value.getVersion(), dependencyCount(CatalogType.TECHNOLOGIES, value.getId()));
    }

    private Item profileItem(ProfessionalCertificationProfileJpaEntity value) {
        return new Item(value.getPublicId(), value.getCode(), value.getName(), value.getDescription(),
                value.getStatus(), value.getSortOrder(), "GLOBAL", null, "GLOBAL",
                value.getSuggestedTechnologicalProfile(),
                value.getCreatedBy(), value.getUpdatedBy(), value.getCreatedAt(), value.getUpdatedAt(),
                value.getVersion(), dependencyCount(CatalogType.PROFESSIONAL_PROFILES, value.getId()));
    }

    private Item technologicalProfileItem(TechnologicalProfileCatalogJpaEntity value) {
        return new Item(value.getPublicId(), value.getCode(), value.getName(), value.getDescription(),
                value.getStatus(), value.getDisplayOrder(), "GLOBAL", null, "GLOBAL", null,
                value.getCreatedBy(), value.getUpdatedBy(), value.getCreatedAt(), value.getUpdatedAt(),
                value.getVersion(), dependencyCount(CatalogType.TECHNOLOGICAL_PROFILES, value.getCode()));
    }

    private Item typeItem(QuestionTypeJpaEntity value) {
        return new Item(value.getCode(), value.getCode(), value.getName(), value.getDescription(),
                value.getStatus().name(), value.getDisplayOrder(), "GLOBAL", null, "GLOBAL", null,
                value.getCreatedBy(), value.getUpdatedBy(), value.getCreatedAt(), value.getUpdatedAt(),
                value.getVersion(), dependencyCount(CatalogType.QUESTION_TYPES, value.getCode()));
    }

    private Item difficultyItem(QuestionDifficultyJpaEntity value) {
        return new Item(value.getCode(), value.getCode(), value.getName(), value.getDescription(),
                value.getStatus().name(), value.getSortOrder(), "GLOBAL", null, "GLOBAL", null,
                value.getCreatedBy(), value.getUpdatedBy(), value.getCreatedAt(), value.getUpdatedAt(),
                value.getVersion(), dependencyCount(CatalogType.DIFFICULTIES, value.getCode()));
    }

    private void assertUnique(CatalogType type, String excludedId, String code, String name,
            String organizationPublicId) {
        String normalizedName = normalizeName(name);
        String requestedOwner = normalizeOrganization(organizationPublicId);
        boolean duplicate = itemsWithoutSecurity(type).stream()
                .filter(value -> excludedId == null || !value.id().equals(excludedId))
                .anyMatch(value -> {
                    boolean duplicatedCode = value.code().equalsIgnoreCase(code);
                    boolean sameOwner = type != CatalogType.CATEGORIES
                            || Objects.equals(categoryOwnerKey(value), requestedOwner);
                    boolean duplicatedName = sameOwner && normalizeName(value.name()).equals(normalizedName);
                    return duplicatedCode || duplicatedName;
                });
        if (duplicate) {
            throw new BusinessException("CATALOG_DUPLICATE",
                    "Ya existe un registro con ese nombre o código dentro del catálogo seleccionado.");
        }
    }

    private long dependencyCount(CatalogType type, Object key) {
        String sql = switch (type) {
            case CATEGORIES -> "SELECT "
                    + "(SELECT COUNT(*) FROM QUESTION_CATEGORY_RELATION WHERE CATEGORY_ID = :key) + "
                    + "(SELECT COUNT(*) FROM COLLECTION_CATEGORY_RELATION WHERE CATEGORY_ID = :key) + "
                    + "(SELECT COUNT(*) FROM FORM_QUESTION_POOL WHERE CATEGORY_ID = :key) + "
                    + "(SELECT COUNT(*) FROM QUESTION_CATEGORY WHERE SOURCE_GLOBAL_ID = :key) FROM DUAL";
            case TECHNOLOGIES -> "SELECT (SELECT COUNT(*) FROM QUESTION WHERE TECHNOLOGY_ID = :key) + "
                    + "(SELECT COUNT(*) FROM STUDENT_CERTIFICATION_PROFILE profile "
                    + "JOIN CERTIFICATION_TECHNOLOGY_CATALOG certification "
                    + "ON certification.CERTIFICATION_TECHNOLOGY_ID = profile.CERTIFICATION_TECHNOLOGY_ID "
                    + "WHERE certification.MASTER_TECHNOLOGY_ID = :key) FROM DUAL";
            case PROFESSIONAL_PROFILES -> "SELECT COUNT(*) FROM STUDENT_CERTIFICATION_PROFILE WHERE PROFESSIONAL_PROFILE_ID = :key";
            case TECHNOLOGICAL_PROFILES -> "SELECT (SELECT COUNT(*) FROM STUDENT_CERTIFICATION_PROFILE WHERE TECHNOLOGICAL_PROFILE = :key) + "
                    + "(SELECT COUNT(*) FROM CERTIFICATION_PROFILE_CATALOG WHERE SUGGESTED_TECH_PROFILE = :key) FROM DUAL";
            case QUESTION_TYPES -> "SELECT COUNT(*) FROM QUESTION WHERE TYPE_CODE = :key";
            case DIFFICULTIES -> "SELECT COUNT(*) FROM QUESTION WHERE DIFFICULTY_CODE = :key";
        };
        Long result = jdbc.queryForObject(sql, new MapSqlParameterSource("key", key), Long.class);
        return result == null ? 0 : result;
    }

    private Object dependencyKey(CatalogType type, String id) {
        return switch (type) {
            case CATEGORIES -> requireCategory(id).getId();
            case TECHNOLOGIES -> requireTechnology(id).getId();
            case PROFESSIONAL_PROFILES -> requireProfile(id).getId();
            case TECHNOLOGICAL_PROFILES -> requireTechnologicalProfile(id).getCode();
            case QUESTION_TYPES, DIFFICULTIES -> id;
        };
    }

    private String dependencyLabel(CatalogType type, long count) {
        return switch (type) {
            case CATEGORIES -> count + " preguntas relacionadas";
            case TECHNOLOGIES -> count + " usos en preguntas o certificaciones";
            case PROFESSIONAL_PROFILES -> count + " perfiles de estudiantes";
            case TECHNOLOGICAL_PROFILES -> count + " perfiles o sugerencias";
            case QUESTION_TYPES, DIFFICULTIES -> count + " preguntas relacionadas";
        };
    }

    private QuestionCategoryJpaEntity requireCategory(String id) {
        return categories.findByPublicId(id).filter(value -> value.getStatus() != CatalogStatus.DELETED)
                .orElseThrow(() -> notFound());
    }
    private QuestionTechnologyJpaEntity requireTechnology(String id) {
        return technologies.findByPublicId(id).orElseThrow(() -> notFound());
    }
    private ProfessionalCertificationProfileJpaEntity requireProfile(String id) {
        return profiles.findByPublicId(id).orElseThrow(() -> notFound());
    }
    private TechnologicalProfileCatalogJpaEntity requireTechnologicalProfile(String id) {
        return technologicalProfiles.findByPublicId(id).orElseThrow(() -> notFound());
    }
    private QuestionTypeJpaEntity requireType(String id) {
        return types.findById(id).orElseThrow(() -> notFound());
    }
    private QuestionDifficultyJpaEntity requireDifficulty(String id) {
        return difficulties.findById(id).orElseThrow(() -> notFound());
    }

    private String currentCode(CatalogType type, String id) {
        return getWithoutSecurity(type, id).code();
    }

    private Item getWithoutSecurity(CatalogType type, String id) {
        return switch (type) {
            case CATEGORIES -> categoryItem(requireCategory(id));
            case TECHNOLOGIES -> technologyItem(requireTechnology(id));
            case PROFESSIONAL_PROFILES -> profileItem(requireProfile(id));
            case TECHNOLOGICAL_PROFILES -> technologicalProfileItem(requireTechnologicalProfile(id));
            case QUESTION_TYPES -> typeItem(requireType(id));
            case DIFFICULTIES -> difficultyItem(requireDifficulty(id));
        };
    }

    private BusinessException notFound() {
        return new BusinessException("CATALOG_ITEM_NOT_FOUND", "El registro de catálogo solicitado no existe.");
    }

    private void validate(Upsert request) {
        if (request == null || request.code() == null || request.code().isBlank()) {
            throw new BusinessException("CATALOG_CODE_REQUIRED", "El código es obligatorio.");
        }
        if (request.name() == null || request.name().isBlank()) {
            throw new BusinessException("CATALOG_NAME_REQUIRED", "El nombre es obligatorio.");
        }
    }

    private String parseStatus(String status) {
        if (status == null || status.isBlank() || ACTIVE.equalsIgnoreCase(status)) return ACTIVE;
        if ("ALL".equalsIgnoreCase(status)) return null;
        return requireOperationalStatus(status);
    }

    private String requireOperationalStatus(String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!ACTIVE.equals(normalized) && !INACTIVE.equals(normalized)) {
            throw new BusinessException("CATALOG_STATUS_INVALID", "El estado indicado no es válido.");
        }
        return normalized;
    }

    private boolean matchesStatus(String current, String expected) {
        return expected == null || expected.equals(current);
    }

    private void requireGlobalAdministrator(TenantContext tenant) {
        if (tenant == null || !tenant.globalAdministrator()) {
            throw new BusinessException("CATALOG_ADMIN_FORBIDDEN",
                    "Solo el Administrador global puede administrar los catálogos maestros.");
        }
    }

    private void checkVersion(Long current, Long expected) {
        if (expected == null || !Objects.equals(current, expected)) {
            throw new BusinessException("CATALOG_CONCURRENT_MODIFICATION",
                    "La información fue modificada por otra sesión. Actualiza la página.");
        }
    }


    private String categoryOwnerKey(Item value) {
        return "GLOBAL".equals(value.scope()) ? "GLOBAL" : normalizeOrganization(value.organizationPublicId());
    }

    private String normalizeOrganization(String value) {
        return value == null || value.isBlank() ? "GLOBAL" : value.trim();
    }

    private String normalizeCode(String value) {
        String result = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (result.isBlank() || result.length() > 120) {
            throw new BusinessException("CATALOG_CODE_INVALID", "El código no es válido.");
        }
        return result;
    }

    private String normalizeName(String value) {
        return Normalizer.normalize(value == null ? "" : value.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private String clean(String value, int max) {
        String result = value.trim().replaceAll("\\s+", " ");
        if (result.length() > max) throw new BusinessException("CATALOG_NAME_TOO_LONG", "El nombre es demasiado largo.");
        return result;
    }

    private String cleanOptional(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String result = value.trim();
        if (result.length() > max) throw new BusinessException("CATALOG_DESCRIPTION_TOO_LONG", "La descripción es demasiado larga.");
        return result;
    }

    private int order(Integer value) { return value == null ? 0 : Math.max(0, value); }

    private void synchronizeCertificationTechnology(QuestionTechnologyJpaEntity master, Instant now) {
        CertificationTechnologyJpaEntity bridge = certificationTechnologies.findByMasterTechnologyId(master.getId())
                .or(() -> certificationTechnologies.findByCodeIgnoreCase(master.getCode()))
                .orElseGet(() -> CertificationTechnologyJpaEntity.createFromMaster(
                        UUID.randomUUID().toString(), master.getCode(), master.getName(),
                        master.getStatus().name(), master.getDisplayOrder(), master.getId(), now));
        bridge.synchronizeWithMaster(master.getName(), master.getStatus().name(),
                master.getDisplayOrder(), master.getId(), now);
        certificationTechnologies.saveAndFlush(bridge);
    }

    private String parseSuggestedProfile(String value) {
        if (value == null || value.isBlank()) return null;
        String code = normalizeCode(value);
        TechnologicalProfileCatalogJpaEntity profile = technologicalProfiles.findByCode(code)
                .orElseThrow(() -> new BusinessException("CATALOG_TECH_PROFILE_INVALID",
                        "El perfil tecnológico sugerido no existe."));
        if (!ACTIVE.equals(profile.getStatus())) {
            throw new BusinessException("CATALOG_TECH_PROFILE_INACTIVE",
                    "El perfil tecnológico sugerido se encuentra inactivo.");
        }
        return profile.getCode();
    }

    private void record(Long actorId, String eventType, CatalogType type, Item item, String description) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("catalogType", type.name());
        values.put("catalogItemId", item.id());
        values.put("code", item.code());
        values.put("name", item.name());
        values.put("status", item.status());
        audit.record(actorId, eventType, "CATALOGS", description, null, null, values, clock.instant());
    }
}
