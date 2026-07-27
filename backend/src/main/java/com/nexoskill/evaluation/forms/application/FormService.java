package com.nexoskill.evaluation.forms.application;

import com.nexoskill.evaluation.forms.infrastructure.FormJpaEntity;
import com.nexoskill.evaluation.forms.infrastructure.FormRepository;
import com.nexoskill.evaluation.globalcontent.application.service.ContentSynchronizationService;
import com.nexoskill.evaluation.globalcontent.application.service.GlobalContentAccessPolicy;
import com.nexoskill.evaluation.globalcontent.domain.model.GlobalContentType;
import com.nexoskill.evaluation.organizations.application.TenantContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FormService {
    private static final Set<String> ALLOWED_STATUSES = Set.of(
            "DRAFT", "ACTIVE", "DISABLED", "CLOSED", "ARCHIVED");

    private final FormRepository repository;
    private final TenantContextResolver tenantContextResolver;
    private final GlobalContentAccessPolicy accessPolicy;
    private final ContentSynchronizationService synchronization;
    private final HttpServletRequest request;

    public FormService(FormRepository repository,
            TenantContextResolver tenantContextResolver,
            GlobalContentAccessPolicy accessPolicy,
            ContentSynchronizationService synchronization,
            HttpServletRequest request) {
        this.repository = repository;
        this.tenantContextResolver = tenantContextResolver;
        this.accessPolicy = accessPolicy;
        this.synchronization = synchronization;
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
        List<FormJpaEntity> source = status == null
                ? repository.findAll()
                : repository.findAllByStatus(status);
        return source.stream()
                .filter(form -> accessPolicy.canRead(GlobalContentType.FORM, form.id, form.contentScope,
                        form.ownerOrganizationId, tenant))
                .sorted(Comparator.comparing(form -> form.title.toLowerCase(Locale.ROOT)))
                .map(form -> new FormModels.FormSummary(form.publicId, form.code, form.title, form.status,
                        form.modeCode, form.passingScore, 0, 0, form.startsAt, form.endsAt, form.version))
                .toList();
    }

    @Transactional(readOnly = true)
    public FormModels.FormDetail get(String publicId) {
        FormJpaEntity form = repository.findByPublicId(canonical(publicId))
                .orElseThrow(() -> new NoSuchElementException("FORM_NOT_FOUND"));
        assertReadable(form);
        return detail(form, List.of());
    }

    @Transactional
    public FormModels.FormDetail create(FormModels.FormCommand command, Long actorUserId) {
        validate(command);
        OffsetDateTime now = OffsetDateTime.now();
        var ownership = accessPolicy.ownershipForCreation(tenantContextResolver.resolve(request));
        FormJpaEntity form = new FormJpaEntity();
        form.publicId = UUID.randomUUID().toString();
        form.code = uniqueCode(command.title());
        form.status = "DRAFT";
        form.createdBy = actorUserId;
        form.updatedBy = actorUserId;
        form.createdAt = now;
        form.assignOwnership(ownership.scope(), ownership.organizationId());
        apply(form, command, actorUserId, now);
        repository.saveAndFlush(form);
        return detail(form, List.of());
    }

    @Transactional
    public FormModels.FormDetail update(String publicId, FormModels.FormCommand command, Long actorUserId) {
        validate(command);
        FormJpaEntity form = repository.findByPublicId(canonical(publicId))
                .orElseThrow(() -> new NoSuchElementException("FORM_NOT_FOUND"));
        assertEditable(form);
        if (command.version() != null && !Objects.equals(command.version(), form.version)) {
            throw new IllegalStateException("FORM_CONCURRENTLY_MODIFIED");
        }
        if ("ARCHIVED".equals(form.status)) {
            throw new IllegalStateException("FORM_ARCHIVED");
        }
        OffsetDateTime now = OffsetDateTime.now();
        apply(form, command, actorUserId, now);
        markCustomized(form, now);
        repository.saveAndFlush(form);
        return detail(form, List.of());
    }

    @Transactional
    public FormModels.FormDetail changeStatus(String publicId, String status, Long actorUserId) {
        FormJpaEntity form = repository.findByPublicId(canonical(publicId))
                .orElseThrow(() -> new NoSuchElementException("FORM_NOT_FOUND"));
        assertEditable(form);
        String target = normalizeRequiredStatus(status);
        if ("ACTIVE".equals(target) && !form.acceptResponses.equals(1)) {
            throw new IllegalStateException("FORM_RESPONSES_DISABLED");
        }
        OffsetDateTime now = OffsetDateTime.now();
        form.status = target;
        form.updatedBy = actorUserId;
        form.updatedAt = now;
        markCustomized(form, now);
        repository.saveAndFlush(form);
        return detail(form, List.of());
    }

    private void assertReadable(FormJpaEntity form) {
        accessPolicy.assertReadable(GlobalContentType.FORM, form.id, form.contentScope, form.ownerOrganizationId,
                tenantContextResolver.resolve(request), "FORM_NOT_FOUND", "El formulario solicitado no existe.");
    }

    private void assertEditable(FormJpaEntity form) {
        accessPolicy.assertEditable(GlobalContentType.FORM, form.id, form.contentScope, form.ownerOrganizationId,
                form.sourceGlobalId, tenantContextResolver.resolve(request));
    }

    private void markCustomized(FormJpaEntity form, OffsetDateTime now) {
        if (form.sourceGlobalId == null) return;
        form.markCustomized(now);
        synchronization.markCustomized(GlobalContentType.FORM, form.ownerOrganizationId, form.id);
    }

    private void validate(FormModels.FormCommand command) {
        if (command == null || command.title() == null || command.title().trim().length() < 3) {
            throw new IllegalArgumentException("FORM_TITLE_REQUIRED");
        }
        if (command.passingScore() == null || command.passingScore().signum() < 0
                || command.passingScore().compareTo(new java.math.BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("FORM_PASSING_SCORE_INVALID");
        }
        if (command.startsAt() != null && command.endsAt() != null
                && !command.endsAt().isAfter(command.startsAt())) {
            throw new IllegalArgumentException("FORM_DATES_INVALID");
        }
        if (command.durationMinutes() != null && command.durationMinutes() <= 0) {
            throw new IllegalArgumentException("FORM_DURATION_INVALID");
        }
        if (command.maxAttempts() != null && command.maxAttempts() <= 0) {
            throw new IllegalArgumentException("FORM_ATTEMPTS_INVALID");
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

    private FormModels.FormDetail detail(FormJpaEntity form, List<FormModels.SectionView> sections) {
        return new FormModels.FormDetail(form.publicId, form.code, form.title, form.description, form.status,
                form.modeCode, form.passingScore, form.maxAttempts, form.retryUntilPassed == 1,
                form.acceptResponses == 1, form.startsAt, form.endsAt, form.durationMinutes,
                form.showResults == 1, form.showCorrectAnswers == 1, form.randomizeQuestions == 1,
                form.randomizeOptions == 1, form.showProgress == 1, form.hideQuestionNumbers == 1,
                form.allowSaveResume == 1, form.oneActiveAttempt == 1, form.thankYouMessage,
                form.version, sections);
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

    private static String normalizeListStatus(String value) {
        if (value == null || value.isBlank()) return "ACTIVE";
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if ("ALL".equals(normalized)) return null;
        if (!ALLOWED_STATUSES.contains(normalized)) {
            throw new IllegalArgumentException("FORM_STATUS_INVALID");
        }
        return normalized;
    }

    private static String normalizeRequiredStatus(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_STATUSES.contains(normalized)) {
            throw new IllegalArgumentException("FORM_STATUS_INVALID");
        }
        return normalized;
    }

    private static String canonical(String id) {
        return UUID.fromString(id.trim()).toString();
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
