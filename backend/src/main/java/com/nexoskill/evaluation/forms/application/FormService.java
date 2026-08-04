package com.nexoskill.evaluation.forms.application;

import com.nexoskill.evaluation.forms.domain.FormContentMode;
import com.nexoskill.evaluation.forms.infrastructure.FormJpaEntity;
import com.nexoskill.evaluation.forms.infrastructure.FormRepository;
import com.nexoskill.evaluation.globalcontent.application.service.ContentSynchronizationService;
import com.nexoskill.evaluation.globalcontent.application.service.GlobalContentAccessPolicy;
import com.nexoskill.evaluation.globalcontent.domain.model.DistributionMode;
import com.nexoskill.evaluation.globalcontent.domain.model.EditorialStatus;
import com.nexoskill.evaluation.globalcontent.domain.model.GlobalContentType;
import com.nexoskill.evaluation.globalcontent.domain.model.GrantStatus;
import com.nexoskill.evaluation.organizations.application.TenantContextResolver;
import com.nexoskill.evaluation.organizations.domain.model.ContentScope;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import java.text.Normalizer;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FormService {
    private static final Set<String> ALLOWED_STATUSES = Set.of(
            "DRAFT", "ACTIVE", "DISABLED", "CLOSED", "ARCHIVED");
    private static final Set<String> ALLOWED_MODES = Set.of("ASSESSMENT", "PRACTICE");

    private final FormRepository repository;
    private final TenantContextResolver tenantContextResolver;
    private final GlobalContentAccessPolicy accessPolicy;
    private final ContentSynchronizationService synchronization;
    private final FormCreationTargetResolver targetResolver;
    private final FormContentManager contentManager;
    private final HttpServletRequest request;

    public FormService(FormRepository repository,
            TenantContextResolver tenantContextResolver,
            GlobalContentAccessPolicy accessPolicy,
            ContentSynchronizationService synchronization,
            FormCreationTargetResolver targetResolver,
            FormContentManager contentManager,
            HttpServletRequest request) {
        this.repository = repository;
        this.tenantContextResolver = tenantContextResolver;
        this.accessPolicy = accessPolicy;
        this.synchronization = synchronization;
        this.targetResolver = targetResolver;
        this.contentManager = contentManager;
        this.request = request;
    }

    @Transactional(readOnly = true)
    public List<FormModels.FormSummary> list() {
        return list("ACTIVE");
    }

    @Transactional(readOnly = true)
    public List<FormModels.FormSummary> list(String requestedStatus) {
        String status = normalizeListStatus(requestedStatus);
        var tenant = tenantContextResolver.resolve(request);
        List<FormJpaEntity> source = status == null ? repository.findAll() : repository.findAllByStatus(status);
        return source.stream()
                .filter(form -> accessPolicy.canRead(GlobalContentType.FORM, form.id, form.contentScope,
                        form.ownerOrganizationId, tenant))
                .sorted(Comparator.comparing(form -> form.title.toLowerCase(Locale.ROOT)))
                .map(this::summary)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<FormModels.FormSummary> list(String query, String requestedStatus, String requestedMode,
            Pageable pageable) {
        String normalizedStatus = normalizeListStatus(requestedStatus);
        String normalizedQuery = query == null || query.isBlank()
                ? null : "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
        String normalizedMode = requestedMode == null || requestedMode.isBlank()
                ? null : requestedMode.trim().toUpperCase(Locale.ROOT);
        if (normalizedMode != null && !ALLOWED_MODES.contains(normalizedMode)) {
            throw new BusinessException("FORM_MODE_INVALID", "La modalidad del formulario no es válida.");
        }
        var tenant = tenantContextResolver.resolve(request);
        return repository.searchVisible(normalizedQuery, normalizedStatus, normalizedMode,
                        tenant.globalScope(), tenant.organizationId(), accessPolicy.allowsAllGlobal(tenant),
                        ContentScope.ORGANIZATION, ContentScope.GLOBAL, GlobalContentType.FORM,
                        GrantStatus.ACTIVE, DistributionMode.GLOBAL_REFERENCE, EditorialStatus.PUBLISHED,
                        Instant.now(), pageable)
                .map(this::summary);
    }

    @Transactional(readOnly = true)
    public FormModels.FormDetail get(String publicId) {
        FormJpaEntity form = find(publicId);
        assertReadable(form);
        return detail(form);
    }

    @Transactional
    public FormModels.FormDetail create(FormModels.FormCommand command, Long actorUserId) {
        validateHeader(command);
        String operationId = operationId(command.operationId());
        FormJpaEntity previous = repository.findByCreateOperationId(operationId).orElse(null);
        if (previous != null) {
            assertReadable(previous);
            return detail(previous);
        }

        var tenant = tenantContextResolver.resolve(request);
        FormCreationTargetResolver.Target target = targetResolver.resolve(tenant,
                command.contentScope(), command.organizationPublicId());
        FormContentMode contentMode = FormContentMode.parse(command.contentMode());
        FormContentManager.ResolvedContent content = contentManager.validate(contentMode,
                questions(command), pools(command), target, Set.of());

        OffsetDateTime now = OffsetDateTime.now();
        FormJpaEntity form = new FormJpaEntity();
        form.publicId = UUID.randomUUID().toString();
        form.code = uniqueCode(command.title());
        form.status = "ACTIVE";
        form.contentMode = contentMode.name();
        form.createOperationId = operationId;
        form.createdBy = actorUserId;
        form.updatedBy = actorUserId;
        form.createdAt = now;
        form.assignOwnership(target.scope(), target.organizationId());
        apply(form, command, actorUserId, now);
        repository.saveAndFlush(form);
        contentManager.replace(form.id, contentMode, content.questions(), content.pools());
        return detail(form);
    }

    @Transactional
    public FormModels.FormDetail update(String publicId, FormModels.FormCommand command, Long actorUserId) {
        validateHeader(command);
        FormJpaEntity form = repository.findByPublicIdForUpdate(canonical(publicId))
                .orElseThrow(() -> new BusinessException("FORM_NOT_FOUND", "El formulario solicitado no existe."));
        assertEditable(form);
        if (command.version() != null && !Objects.equals(command.version(), form.version)) {
            throw new BusinessException("FORM_CONCURRENTLY_MODIFIED",
                    "El formulario fue modificado por otra sesión. Actualiza la página e intenta nuevamente.");
        }
        if ("ARCHIVED".equals(form.status)) {
            throw new BusinessException("FORM_ARCHIVED", "Un formulario archivado no puede editarse.");
        }
        assertOwnershipUnchanged(form, command);
        FormCreationTargetResolver.Target target = targetResolver.fromExisting(form.contentScope,
                form.ownerOrganizationId);
        FormContentManager.Snapshot current = contentManager.load(form.id, target);
        FormContentMode contentMode = FormContentMode.parse(command.contentMode());
        FormContentManager.ResolvedContent content = contentManager.validate(contentMode,
                questions(command), pools(command), target, current.questionPublicIds());

        OffsetDateTime now = OffsetDateTime.now();
        form.contentMode = contentMode.name();
        apply(form, command, actorUserId, now);
        markCustomized(form, now);
        repository.saveAndFlush(form);
        contentManager.replace(form.id, contentMode, content.questions(), content.pools());
        return detail(form);
    }

    @Transactional
    public FormModels.FormDetail clone(String publicId, FormModels.CloneCommand command, Long actorUserId) {
        if (command == null) {
            throw new BusinessException("FORM_CLONE_REQUEST_REQUIRED",
                    "Indica la configuración de la copia del formulario.");
        }
        String operationId = operationId(command.operationId());
        FormJpaEntity previous = repository.findByCreateOperationId(operationId).orElse(null);
        if (previous != null) {
            assertReadable(previous);
            return detail(previous);
        }

        FormJpaEntity source = repository.findByPublicIdForUpdate(canonical(publicId))
                .orElseThrow(() -> new BusinessException("FORM_NOT_FOUND", "El formulario solicitado no existe."));
        assertReadable(source);
        if ("ARCHIVED".equals(source.status)) {
            throw new BusinessException("FORM_ARCHIVED", "Un formulario archivado no puede clonarse.");
        }
        var tenant = tenantContextResolver.resolve(request);
        FormCreationTargetResolver.Target target = targetResolver.resolve(tenant,
                command.targetScope(), command.organizationPublicId());
        FormCreationTargetResolver.Target sourceTarget = targetResolver.fromExisting(source.contentScope,
                source.ownerOrganizationId);
        FormContentManager.Snapshot sourceContent = contentManager.load(source.id, sourceTarget);
        FormContentMode contentMode = FormContentMode.parse(source.contentMode);
        List<FormModels.QuestionItem> questionItems = sourceContent.questions().stream()
                .map(item -> new FormModels.QuestionItem(item.questionPublicId(), item.order(),
                        item.points(), item.required())).toList();
        List<FormModels.PoolItem> poolItems = sourceContent.pools().stream()
                .map(item -> new FormModels.PoolItem(item.publicId(), "CATEGORY", item.categoryPublicId(),
                        item.questionCount(), item.difficultyCode(), item.order())).toList();
        FormContentManager.ResolvedContent validated = contentManager.validate(contentMode,
                questionItems, poolItems, target, Set.of());

        OffsetDateTime now = OffsetDateTime.now();
        FormJpaEntity copy = new FormJpaEntity();
        copy.publicId = UUID.randomUUID().toString();
        copy.title = cloneTitle(command.title(), source.title);
        copy.code = uniqueCode(copy.title);
        copy.description = source.description;
        copy.status = "ACTIVE";
        copy.modeCode = source.modeCode;
        copy.passingScore = source.passingScore;
        copy.maxAttempts = source.maxAttempts;
        copy.retryUntilPassed = source.retryUntilPassed;
        copy.acceptResponses = source.acceptResponses;
        copy.startsAt = source.startsAt;
        copy.endsAt = source.endsAt;
        copy.durationMinutes = source.durationMinutes;
        copy.showResults = source.showResults;
        copy.showCorrectAnswers = source.showCorrectAnswers;
        copy.randomizeQuestions = source.randomizeQuestions;
        copy.randomizeOptions = source.randomizeOptions;
        copy.showProgress = source.showProgress;
        copy.hideQuestionNumbers = source.hideQuestionNumbers;
        copy.allowSaveResume = source.allowSaveResume;
        copy.oneActiveAttempt = source.oneActiveAttempt;
        copy.thankYouMessage = source.thankYouMessage;
        copy.contentMode = contentMode.name();
        copy.createOperationId = operationId;
        copy.createdBy = actorUserId;
        copy.updatedBy = actorUserId;
        copy.createdAt = now;
        copy.updatedAt = now;
        copy.assignOwnership(target.scope(), target.organizationId());
        repository.saveAndFlush(copy);
        contentManager.replace(copy.id, contentMode, validated.questions(), validated.pools());
        return detail(copy);
    }

    @Transactional(readOnly = true)
    public List<FormModels.OrganizationOptionView> availableOrganizations() {
        return targetResolver.availableOrganizations(tenantContextResolver.resolve(request));
    }

    @Transactional(readOnly = true)
    public FormModels.QuestionOptionPage questionOptions(String scope, String organizationPublicId,
            String query, String categoryPublicId, int page, int size) {
        var target = targetResolver.resolve(tenantContextResolver.resolve(request), scope, organizationPublicId);
        return contentManager.questionOptions(target, query, categoryPublicId, page, size);
    }

    @Transactional(readOnly = true)
    public List<FormModels.CategoryOptionView> categoryOptions(String scope, String organizationPublicId) {
        var target = targetResolver.resolve(tenantContextResolver.resolve(request), scope, organizationPublicId);
        return contentManager.categoryOptions(target);
    }

    @Transactional
    public FormModels.FormDetail changeStatus(String publicId, String status, Long actorUserId) {
        FormJpaEntity form = repository.findByPublicIdForUpdate(canonical(publicId))
                .orElseThrow(() -> new BusinessException("FORM_NOT_FOUND", "El formulario solicitado no existe."));
        assertEditable(form);
        String target = normalizeRequiredStatus(status);
        if ("ACTIVE".equals(target) && !Integer.valueOf(1).equals(form.acceptResponses)) {
            throw new BusinessException("FORM_RESPONSES_DISABLED",
                    "Habilita la recepción de respuestas antes de activar el formulario.");
        }
        FormCreationTargetResolver.Target owner = targetResolver.fromExisting(form.contentScope,
                form.ownerOrganizationId);
        FormContentManager.Counts counts = contentManager.counts(form.id);
        if ("ACTIVE".equals(target) && counts.questionCount() == 0 && counts.poolCount() == 0) {
            throw new BusinessException("FORM_CONTENT_REQUIRED",
                    "Configura preguntas manuales o un Pool aleatorio antes de activar el formulario.");
        }
        OffsetDateTime now = OffsetDateTime.now();
        form.status = target;
        form.updatedBy = actorUserId;
        form.updatedAt = now;
        markCustomized(form, now);
        repository.saveAndFlush(form);
        return detail(form, owner);
    }

    private FormModels.FormSummary summary(FormJpaEntity form) {
        FormCreationTargetResolver.Target owner = targetResolver.fromExisting(form.contentScope,
                form.ownerOrganizationId);
        FormContentManager.Counts counts = contentManager.counts(form.id);
        return new FormModels.FormSummary(form.publicId, form.code, form.title, form.status,
                form.modeCode, form.passingScore, form.contentScope.name(), owner.organizationPublicId(),
                owner.organizationName(), FormContentMode.parse(form.contentMode).name(),
                counts.questionCount(), counts.poolCount(), form.startsAt, form.endsAt, form.version);
    }

    private FormModels.FormDetail detail(FormJpaEntity form) {
        return detail(form, targetResolver.fromExisting(form.contentScope, form.ownerOrganizationId));
    }

    private FormModels.FormDetail detail(FormJpaEntity form, FormCreationTargetResolver.Target owner) {
        FormContentManager.Snapshot content = contentManager.load(form.id, owner);
        return new FormModels.FormDetail(form.publicId, form.code, form.title, form.description, form.status,
                form.modeCode, form.passingScore, form.maxAttempts, Integer.valueOf(1).equals(form.retryUntilPassed),
                Integer.valueOf(1).equals(form.acceptResponses), form.startsAt, form.endsAt, form.durationMinutes,
                Integer.valueOf(1).equals(form.showResults), Integer.valueOf(1).equals(form.showCorrectAnswers),
                Integer.valueOf(1).equals(form.randomizeQuestions), Integer.valueOf(1).equals(form.randomizeOptions),
                Integer.valueOf(1).equals(form.showProgress), Integer.valueOf(1).equals(form.hideQuestionNumbers),
                Integer.valueOf(1).equals(form.allowSaveResume), Integer.valueOf(1).equals(form.oneActiveAttempt),
                form.thankYouMessage, form.version, form.contentScope.name(), owner.organizationPublicId(),
                owner.organizationName(), FormContentMode.parse(form.contentMode).name(),
                content.questions(), content.pools());
    }

    private FormJpaEntity find(String publicId) {
        return repository.findByPublicId(canonical(publicId))
                .orElseThrow(() -> new BusinessException("FORM_NOT_FOUND", "El formulario solicitado no existe."));
    }

    private void assertReadable(FormJpaEntity form) {
        accessPolicy.assertReadable(GlobalContentType.FORM, form.id, form.contentScope, form.ownerOrganizationId,
                tenantContextResolver.resolve(request), "FORM_NOT_FOUND", "El formulario solicitado no existe.");
    }

    private void assertEditable(FormJpaEntity form) {
        accessPolicy.assertEditable(GlobalContentType.FORM, form.id, form.contentScope, form.ownerOrganizationId,
                form.sourceGlobalId, tenantContextResolver.resolve(request));
    }

    private void assertOwnershipUnchanged(FormJpaEntity form, FormModels.FormCommand command) {
        if (command.contentScope() != null && !command.contentScope().isBlank()
                && !form.contentScope.name().equalsIgnoreCase(command.contentScope().trim())) {
            throw new BusinessException("FORM_OWNER_IMMUTABLE",
                    "El alcance y la organización propietaria no pueden cambiarse durante la edición.");
        }
        if (command.organizationPublicId() != null && !command.organizationPublicId().isBlank()) {
            var owner = targetResolver.fromExisting(form.contentScope, form.ownerOrganizationId);
            if (!owner.organizationPublicId().equals(command.organizationPublicId().trim())) {
                throw new BusinessException("FORM_OWNER_IMMUTABLE",
                        "El alcance y la organización propietaria no pueden cambiarse durante la edición.");
            }
        }
    }

    private void markCustomized(FormJpaEntity form, OffsetDateTime now) {
        if (form.sourceGlobalId == null) return;
        form.markCustomized(now);
        synchronization.markCustomized(GlobalContentType.FORM, form.ownerOrganizationId, form.id);
    }

    private void validateHeader(FormModels.FormCommand command) {
        Map<String, String> errors = new java.util.LinkedHashMap<>();
        if (command == null || command.title() == null || command.title().trim().length() < 3) {
            errors.put("title", "Agrega un título de al menos 3 caracteres.");
        }
        if (command == null || command.passingScore() == null || command.passingScore().signum() < 0
                || command.passingScore().compareTo(new java.math.BigDecimal("100")) > 0) {
            errors.put("passingScore", "El puntaje mínimo debe estar entre 0 y 100.");
        }
        if (command != null && command.startsAt() != null && command.endsAt() != null
                && !command.endsAt().isAfter(command.startsAt())) {
            errors.put("endsAt", "La fecha de cierre debe ser posterior al inicio.");
        }
        if (command != null && command.durationMinutes() != null && command.durationMinutes() <= 0) {
            errors.put("durationMinutes", "La duración debe ser mayor que cero.");
        }
        if (command != null && command.maxAttempts() != null && command.maxAttempts() <= 0) {
            errors.put("maxAttempts", "Los intentos máximos deben ser mayores que cero.");
        }
        if (command != null && command.modeCode() != null
                && !ALLOWED_MODES.contains(command.modeCode().trim().toUpperCase(Locale.ROOT))) {
            errors.put("modeCode", "La modalidad del formulario no es válida.");
        }
        if (!errors.isEmpty()) {
            throw new BusinessException("FORM_VALIDATION_ERROR",
                    "Revisa la información obligatoria del formulario.", errors);
        }
    }

    private void apply(FormJpaEntity form, FormModels.FormCommand command, Long actorUserId, OffsetDateTime now) {
        form.title = command.title().trim();
        form.description = trim(command.description());
        form.modeCode = upper(command.modeCode(), "ASSESSMENT");
        form.passingScore = command.passingScore();
        form.maxAttempts = command.maxAttempts();
        form.retryUntilPassed = flag(command.retryUntilPassed());
        form.acceptResponses = flag(command.acceptResponses());
        form.startsAt = command.startsAt();
        form.endsAt = command.endsAt();
        form.durationMinutes = command.durationMinutes();
        form.showResults = flag(command.showResults());
        form.showCorrectAnswers = flag(command.showCorrectAnswers());
        form.randomizeQuestions = flag(command.randomizeQuestions());
        form.randomizeOptions = flag(command.randomizeOptions());
        form.showProgress = flag(command.showProgress());
        form.hideQuestionNumbers = flag(command.hideQuestionNumbers());
        form.allowSaveResume = flag(command.allowSaveResume());
        form.oneActiveAttempt = flag(command.oneActiveAttempt());
        form.thankYouMessage = trim(command.thankYouMessage());
        form.updatedBy = actorUserId;
        form.updatedAt = now;
    }

    private List<FormModels.QuestionItem> questions(FormModels.FormCommand command) {
        if (command.questions() != null && !command.questions().isEmpty()) return command.questions();
        if (command.sections() == null) return List.of();
        List<FormModels.QuestionItem> result = new ArrayList<>();
        command.sections().stream()
                .sorted(Comparator.comparing(section -> section.order() == null ? Integer.MAX_VALUE : section.order()))
                .forEach(section -> {
                    if (section.questions() != null) result.addAll(section.questions());
                });
        return result;
    }

    private List<FormModels.PoolItem> pools(FormModels.FormCommand command) {
        if (command.pools() != null && !command.pools().isEmpty()) return command.pools();
        if (command.sections() == null) return List.of();
        List<FormModels.PoolItem> result = new ArrayList<>();
        command.sections().stream()
                .sorted(Comparator.comparing(section -> section.order() == null ? Integer.MAX_VALUE : section.order()))
                .forEach(section -> {
                    if (section.pools() != null) result.addAll(section.pools());
                });
        return result;
    }

    private String uniqueCode(String title) {
        String base = Normalizer.normalize(title, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (base.isBlank()) base = "FORM";
        base = base.substring(0, Math.min(base.length(), 60));
        String code = base;
        int sequence = 2;
        while (repository.existsByCode(code)) code = base + "_" + sequence++;
        return code;
    }

    private static String cloneTitle(String requested, String sourceTitle) {
        String value = requested == null || requested.isBlank() ? sourceTitle + " (copia)" : requested.trim();
        if (value.length() < 3 || value.length() > 200) {
            throw new BusinessException("FORM_CLONE_TITLE_INVALID",
                    "El nombre de la copia debe tener entre 3 y 200 caracteres.");
        }
        return value;
    }

    private static String operationId(String value) {
        if (value == null || value.isBlank()) return UUID.randomUUID().toString();
        try {
            return UUID.fromString(value.trim()).toString();
        } catch (Exception exception) {
            throw new BusinessException("FORM_OPERATION_ID_INVALID",
                    "No fue posible identificar de forma segura la operación solicitada.");
        }
    }

    private static String normalizeListStatus(String value) {
        if (value == null || value.isBlank()) return "ACTIVE";
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if ("ALL".equals(normalized)) return null;
        if (!ALLOWED_STATUSES.contains(normalized)) {
            throw new BusinessException("FORM_STATUS_INVALID", "El estado del formulario no es válido.");
        }
        return normalized;
    }

    private static String normalizeRequiredStatus(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_STATUSES.contains(normalized)) {
            throw new BusinessException("FORM_STATUS_INVALID", "El estado del formulario no es válido.");
        }
        return normalized;
    }

    private static String canonical(String id) {
        try {
            return UUID.fromString(id.trim()).toString();
        } catch (Exception exception) {
            throw new BusinessException("FORM_ID_INVALID", "El formulario indicado no es válido.");
        }
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static String upper(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim().toUpperCase(Locale.ROOT);
    }

    private static int flag(boolean value) {
        return value ? 1 : 0;
    }
}
