package com.nexoskill.evaluation.forms.application;

import com.nexoskill.evaluation.forms.infrastructure.FormJpaEntity;
import com.nexoskill.evaluation.forms.infrastructure.FormRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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

    @PersistenceContext
    private EntityManager em;

    public FormService(FormRepository repository) {
        this.repository = repository;
    }

    /** Compatibilidad interna: los listados operativos muestran activos por defecto. */
    @Transactional(readOnly = true)
    public List<FormModels.FormSummary> list() {
        return list("ACTIVE");
    }

    @Transactional(readOnly = true)
    public List<FormModels.FormSummary> list(String requestedStatus) {
        String status = normalizeListStatus(requestedStatus);
        List<FormJpaEntity> source = status == null
                ? repository.findAll()
                : repository.findAllByStatus(status);
        return source.stream()
                .sorted(Comparator.comparing(form -> form.title.toLowerCase(Locale.ROOT)))
                .map(form -> new FormModels.FormSummary(form.publicId, form.code, form.title, form.status,
                        form.modeCode, form.passingScore, 0, 0, form.startsAt, form.endsAt, form.version))
                .toList();
    }

    @Transactional(readOnly = true)
    public FormModels.FormDetail get(String publicId) {
        FormJpaEntity form = repository.findByPublicId(canonical(publicId))
                .orElseThrow(() -> new NoSuchElementException("FORM_NOT_FOUND"));
        return detail(form, List.of());
    }

    @Transactional
    public FormModels.FormDetail create(FormModels.FormCommand command) {
        validate(command);
        FormJpaEntity form = new FormJpaEntity();
        form.publicId = UUID.randomUUID().toString();
        form.code = uniqueCode(command.title());
        form.status = "DRAFT";
        form.createdAt = OffsetDateTime.now();
        apply(form, command);
        repository.saveAndFlush(form);
        return detail(form, List.of());
    }

    @Transactional
    public FormModels.FormDetail update(String publicId, FormModels.FormCommand command) {
        validate(command);
        FormJpaEntity form = repository.findByPublicId(canonical(publicId))
                .orElseThrow(() -> new NoSuchElementException("FORM_NOT_FOUND"));
        if (command.version() != null && !Objects.equals(command.version(), form.version)) {
            throw new IllegalStateException("FORM_CONCURRENTLY_MODIFIED");
        }
        if ("ARCHIVED".equals(form.status)) {
            throw new IllegalStateException("FORM_ARCHIVED");
        }
        apply(form, command);
        repository.saveAndFlush(form);
        return detail(form, List.of());
    }

    @Transactional
    public FormModels.FormDetail changeStatus(String publicId, String status) {
        FormJpaEntity form = repository.findByPublicId(canonical(publicId))
                .orElseThrow(() -> new NoSuchElementException("FORM_NOT_FOUND"));
        String target = normalizeRequiredStatus(status);
        if ("ACTIVE".equals(target) && !form.acceptResponses.equals(1)) {
            throw new IllegalStateException("FORM_RESPONSES_DISABLED");
        }
        form.status = target;
        form.updatedAt = OffsetDateTime.now();
        repository.saveAndFlush(form);
        return detail(form, List.of());
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

    private void apply(FormJpaEntity form, FormModels.FormCommand command) {
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
        form.updatedAt = OffsetDateTime.now();
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
