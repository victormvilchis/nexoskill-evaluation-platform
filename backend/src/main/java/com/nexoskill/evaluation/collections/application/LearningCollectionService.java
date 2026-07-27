package com.nexoskill.evaluation.collections.application;

import com.nexoskill.evaluation.collections.infrastructure.LearningCollectionJpaEntity;
import com.nexoskill.evaluation.collections.infrastructure.LearningCollectionLevelJpaEntity;
import com.nexoskill.evaluation.collections.infrastructure.LearningCollectionRepository;
import com.nexoskill.evaluation.forms.infrastructure.FormJpaEntity;
import com.nexoskill.evaluation.forms.infrastructure.FormRepository;
import com.nexoskill.evaluation.globalcontent.application.service.ContentSynchronizationService;
import com.nexoskill.evaluation.globalcontent.application.service.GlobalContentAccessPolicy;
import com.nexoskill.evaluation.globalcontent.domain.model.GlobalContentType;
import com.nexoskill.evaluation.organizations.application.TenantContextResolver;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LearningCollectionService {

    private static final Set<String> VALID_STATUSES =
            Set.of("DRAFT", "ACTIVE", "INACTIVE", "ARCHIVED");

    private final LearningCollectionRepository collectionRepository;
    private final FormRepository formRepository;
    private final TenantContextResolver tenantContextResolver;
    private final GlobalContentAccessPolicy accessPolicy;
    private final ContentSynchronizationService synchronization;
    private final HttpServletRequest request;

    @PersistenceContext
    private EntityManager entityManager;

    public LearningCollectionService(
            LearningCollectionRepository collectionRepository,
            FormRepository formRepository,
            TenantContextResolver tenantContextResolver,
            GlobalContentAccessPolicy accessPolicy,
            ContentSynchronizationService synchronization,
            HttpServletRequest request) {
        this.collectionRepository = collectionRepository;
        this.formRepository = formRepository;
        this.tenantContextResolver = tenantContextResolver;
        this.accessPolicy = accessPolicy;
        this.synchronization = synchronization;
        this.request = request;
    }

    @Transactional(readOnly = true)
    public List<LearningCollectionModels.CollectionSummary> list(
            String query,
            String status) {

        String normalizedQuery = normalizeSearch(query);
        String normalizedStatus = normalizeOptionalStatus(status);

        var tenant = tenantContextResolver.resolve(request);
        return collectionRepository.findAll().stream()
                .filter(collection -> accessPolicy.canRead(GlobalContentType.COLLECTION, collection.id,
                        collection.contentScope, collection.ownerOrganizationId, tenant))
                .filter(collection -> normalizedStatus == null
                        || normalizedStatus.equals(collection.status))
                .filter(collection -> normalizedQuery == null
                        || searchableText(collection).contains(normalizedQuery))
                .sorted(Comparator.comparing(
                        collection -> collection.name.toLowerCase(Locale.ROOT)))
                .map(this::summary)
                .toList();
    }

    @Transactional(readOnly = true)
    public LearningCollectionModels.CollectionDetail get(String publicId) {
        LearningCollectionJpaEntity collection = collectionRepository
                .findByPublicId(canonicalUuid(publicId))
                .orElseThrow(() -> new BusinessException(
                        "COLLECTION_NOT_FOUND",
                        "La colección solicitada no existe."));
        assertReadable(collection);

        collection.levels.size();
        return detail(collection);
    }

    @Transactional(readOnly = true)
    public List<LearningCollectionModels.FormOption> formOptions(
            String query,
            String status) {

        String normalizedQuery = normalizeSearch(query);
        String normalizedStatus = normalizeOptionalFormStatus(status);

        var tenant = tenantContextResolver.resolve(request);
        return formRepository.findAll().stream()
                .filter(form -> accessPolicy.canRead(GlobalContentType.FORM, form.id, form.contentScope,
                        form.ownerOrganizationId, tenant))
                .filter(form -> !"ARCHIVED".equals(form.status))
                .filter(form -> normalizedStatus == null
                        || normalizedStatus.equals(form.status))
                .filter(form -> normalizedQuery == null
                        || formSearchableText(form).contains(normalizedQuery))
                .sorted(Comparator.comparing(
                        form -> form.title.toLowerCase(Locale.ROOT)))
                .map(form -> new LearningCollectionModels.FormOption(
                        form.publicId,
                        form.code,
                        form.title,
                        form.status,
                        form.modeCode,
                        form.passingScore,
                        form.durationMinutes))
                .toList();
    }

    @Transactional
    public LearningCollectionModels.CollectionDetail create(
            LearningCollectionModels.CollectionCommand command,
            Long actorUserId) {

        validateCommand(command);
        ensureUniqueName(command.name(), null);
        OffsetDateTime now = OffsetDateTime.now();
        var ownership = accessPolicy.ownershipForCreation(tenantContextResolver.resolve(request));

        LearningCollectionJpaEntity collection = new LearningCollectionJpaEntity();
        collection.publicId = UUID.randomUUID().toString();
        collection.code = uniqueCode(command.name());
        collection.name = command.name().trim();
        collection.description = trimToNull(command.description());
        collection.status = "DRAFT";
        collection.createdBy = actorUserId;
        collection.updatedBy = actorUserId;
        collection.createdAt = now;
        collection.updatedAt = now;
        collection.assignOwnership(ownership.scope(), ownership.organizationId());
        collection.replaceLevels(buildLevels(command.formPublicIds(), now));

        collectionRepository.saveAndFlush(collection);
        return detail(collection);
    }

    @Transactional
    public LearningCollectionModels.CollectionDetail update(
            String publicId,
            LearningCollectionModels.CollectionCommand command,
            Long actorUserId) {

        validateCommand(command);

        LearningCollectionJpaEntity collection = collectionRepository
                .findByPublicIdForUpdate(canonicalUuid(publicId))
                .orElseThrow(() -> new BusinessException(
                        "COLLECTION_NOT_FOUND",
                        "La colección solicitada no existe."));
        assertEditable(collection);

        if (command.version() != null
                && !Objects.equals(command.version(), collection.version)) {
            throw new BusinessException(
                    "COLLECTION_CONCURRENT_MODIFICATION",
                    "La colección fue modificada por otra sesión. Recarga la página e intenta nuevamente.");
        }

        if ("ARCHIVED".equals(collection.status)) {
            throw new BusinessException(
                    "COLLECTION_ARCHIVED",
                    "Una colección archivada no puede modificarse.");
        }

        ensureUniqueName(command.name(), collection.publicId);
        OffsetDateTime now = OffsetDateTime.now();
        List<LearningCollectionLevelJpaEntity> newLevels =
                buildLevels(command.formPublicIds(), now);

        if ("ACTIVE".equals(collection.status)) {
            validateActivationReadiness(newLevels);
        }

        collection.name = command.name().trim();
        collection.description = trimToNull(command.description());
        collection.updatedBy = actorUserId;
        collection.updatedAt = now;
        markCustomized(collection, now);

        // Oracle valida las restricciones únicas durante el flush. Eliminamos los
        // niveles anteriores antes de insertar el nuevo orden para evitar colisiones.
        collection.levels.clear();
        entityManager.flush();
        collection.replaceLevels(newLevels);

        collectionRepository.saveAndFlush(collection);
        return detail(collection);
    }

    @Transactional
    public LearningCollectionModels.CollectionDetail changeStatus(
            String publicId,
            String targetStatus,
            Long actorUserId) {

        String status = normalizeRequiredStatus(targetStatus);
        LearningCollectionJpaEntity collection = collectionRepository
                .findByPublicIdForUpdate(canonicalUuid(publicId))
                .orElseThrow(() -> new BusinessException(
                        "COLLECTION_NOT_FOUND",
                        "La colección solicitada no existe."));
        assertEditable(collection);

        collection.levels.size();

        if ("ACTIVE".equals(status)) {
            if (collection.levels.isEmpty()) {
                throw new BusinessException(
                        "COLLECTION_EMPTY",
                        "Agrega al menos un formulario antes de activar la colección.");
            }
            validateActivationReadiness(collection.levels);
        }

        OffsetDateTime now = OffsetDateTime.now();
        collection.status = status;
        collection.updatedBy = actorUserId;
        collection.updatedAt = now;
        markCustomized(collection, now);
        collectionRepository.saveAndFlush(collection);
        return detail(collection);
    }

    private void assertReadable(LearningCollectionJpaEntity collection) {
        accessPolicy.assertReadable(GlobalContentType.COLLECTION, collection.id, collection.contentScope,
                collection.ownerOrganizationId, tenantContextResolver.resolve(request),
                "COLLECTION_NOT_FOUND", "La colección solicitada no existe.");
    }

    private void assertEditable(LearningCollectionJpaEntity collection) {
        accessPolicy.assertEditable(GlobalContentType.COLLECTION, collection.id, collection.contentScope,
                collection.ownerOrganizationId, collection.sourceGlobalId, tenantContextResolver.resolve(request));
    }

    private void assertReadable(FormJpaEntity form) {
        accessPolicy.assertReadable(GlobalContentType.FORM, form.id, form.contentScope, form.ownerOrganizationId,
                tenantContextResolver.resolve(request), "COLLECTION_FORM_NOT_FOUND",
                "Uno de los formularios seleccionados ya no está disponible.");
    }

    private void markCustomized(LearningCollectionJpaEntity collection, OffsetDateTime now) {
        if (collection.sourceGlobalId == null) return;
        collection.markCustomized(now);
        synchronization.markCustomized(GlobalContentType.COLLECTION, collection.ownerOrganizationId, collection.id);
    }

    private void validateActivationReadiness(
            List<LearningCollectionLevelJpaEntity> levels) {

        boolean containsUnavailableForm = levels.stream()
                .anyMatch(level -> !"ACTIVE".equals(level.form.status));

        if (containsUnavailableForm) {
            throw new BusinessException(
                    "COLLECTION_CONTAINS_INACTIVE_FORM",
                    "Todos los formularios deben estar activos antes de activar la colección.");
        }
    }

    private void validateCommand(LearningCollectionModels.CollectionCommand command) {
        if (command == null
                || command.name() == null
                || command.name().trim().length() < 3) {
            throw new BusinessException(
                    "COLLECTION_NAME_REQUIRED",
                    "Escribe un nombre de al menos 3 caracteres para la colección.");
        }

        if (command.name().trim().length() > 200) {
            throw new BusinessException(
                    "COLLECTION_NAME_TOO_LONG",
                    "El nombre de la colección no puede exceder 200 caracteres.");
        }

        if (command.description() != null
                && command.description().trim().length() > 2000) {
            throw new BusinessException(
                    "COLLECTION_DESCRIPTION_TOO_LONG",
                    "La descripción no puede exceder 2000 caracteres.");
        }

        if (command.formPublicIds() == null || command.formPublicIds().isEmpty()) {
            throw new BusinessException(
                    "COLLECTION_EMPTY",
                    "Agrega al menos un formulario para construir los niveles.");
        }

        LinkedHashSet<String> uniqueIds = new LinkedHashSet<>();
        for (String publicId : command.formPublicIds()) {
            String canonicalId = canonicalUuid(publicId);
            if (!uniqueIds.add(canonicalId)) {
                throw new BusinessException(
                        "COLLECTION_FORM_DUPLICATED",
                        "Un formulario no puede repetirse dentro de la misma colección.");
            }
        }
    }

    private List<LearningCollectionLevelJpaEntity> buildLevels(
            List<String> publicIds,
            OffsetDateTime now) {

        List<LearningCollectionLevelJpaEntity> levels = new ArrayList<>();

        for (int index = 0; index < publicIds.size(); index++) {
            String formPublicId = canonicalUuid(publicIds.get(index));
            FormJpaEntity form = formRepository.findByPublicId(formPublicId)
                    .orElseThrow(() -> new BusinessException(
                            "COLLECTION_FORM_NOT_FOUND",
                            "Uno de los formularios seleccionados ya no está disponible."));

            assertReadable(form);

            if ("ARCHIVED".equals(form.status)) {
                throw new BusinessException(
                        "COLLECTION_FORM_ARCHIVED",
                        "El formulario «" + form.title + "» está archivado y no puede agregarse.");
            }

            LearningCollectionLevelJpaEntity level = new LearningCollectionLevelJpaEntity();
            level.form = form;
            level.levelOrder = index + 1;
            level.unlockRule = index == 0 ? "FIRST_AVAILABLE" : "PASS_PREVIOUS";
            level.createdAt = now;
            levels.add(level);
        }

        return levels;
    }

    private LearningCollectionModels.CollectionSummary summary(
            LearningCollectionJpaEntity collection) {

        collection.levels.size();
        int activeLevelCount = (int) collection.levels.stream()
                .filter(level -> "ACTIVE".equals(level.form.status))
                .count();

        return new LearningCollectionModels.CollectionSummary(
                collection.publicId,
                collection.code,
                collection.name,
                collection.description,
                collection.status,
                collection.levels.size(),
                activeLevelCount,
                collection.updatedAt,
                collection.version);
    }

    private LearningCollectionModels.CollectionDetail detail(
            LearningCollectionJpaEntity collection) {

        List<LearningCollectionModels.LevelView> levels = collection.levels.stream()
                .sorted(Comparator.comparing(level -> level.levelOrder))
                .map(level -> new LearningCollectionModels.LevelView(
                        level.levelOrder,
                        level.unlockRule,
                        level.form.publicId,
                        level.form.code,
                        level.form.title,
                        level.form.status,
                        level.form.modeCode,
                        level.form.passingScore,
                        level.form.durationMinutes))
                .toList();

        return new LearningCollectionModels.CollectionDetail(
                collection.publicId,
                collection.code,
                collection.name,
                collection.description,
                collection.status,
                levels,
                collection.createdAt,
                collection.updatedAt,
                collection.version);
    }

    private void ensureUniqueName(String name, String currentPublicId) {
        String normalizedName = normalizeForSearch(name.trim()).replaceAll("\\s+", " ");
        boolean duplicated = collectionRepository.findAll().stream()
                .filter(collection -> currentPublicId == null
                        || !currentPublicId.equals(collection.publicId))
                .map(collection -> normalizeForSearch(collection.name).replaceAll("\\s+", " "))
                .anyMatch(normalizedName::equals);

        if (duplicated) {
            throw new BusinessException(
                    "COLLECTION_ALREADY_EXISTS",
                    "Ya existe una colección con ese nombre.");
        }
    }

    private String uniqueCode(String name) {
        String base = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");

        if (base.isBlank()) {
            base = "COLLECTION";
        }

        base = base.substring(0, Math.min(base.length(), 60));
        String candidate = base;
        int suffix = 2;

        while (collectionRepository.existsByCode(candidate)) {
            candidate = base + "_" + suffix++;
        }

        return candidate;
    }

    private static String canonicalUuid(String value) {
        try {
            return UUID.fromString(value == null ? "" : value.trim()).toString();
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    "INVALID_PUBLIC_ID",
                    "El identificador recibido no es válido.");
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String normalizeSearch(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }

    private static String searchableText(LearningCollectionJpaEntity collection) {
        return normalizeForSearch(
                collection.name + " "
                        + collection.code + " "
                        + Objects.toString(collection.description, ""));
    }

    private static String formSearchableText(FormJpaEntity form) {
        return normalizeForSearch(form.title + " " + form.code);
    }

    private static String normalizeForSearch(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }

    private static String normalizeOptionalStatus(String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            return null;
        }
        return normalizeRequiredStatus(status);
    }

    private static String normalizeRequiredStatus(String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!VALID_STATUSES.contains(normalized)) {
            throw new BusinessException(
                    "COLLECTION_STATUS_INVALID",
                    "El estado solicitado para la colección no es válido.");
        }
        return normalized;
    }

    private static String normalizeOptionalFormStatus(String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            return null;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("DRAFT", "ACTIVE", "DISABLED", "CLOSED").contains(normalized)) {
            throw new BusinessException(
                    "FORM_STATUS_INVALID",
                    "El filtro de estado del formulario no es válido.");
        }
        return normalized;
    }
}
