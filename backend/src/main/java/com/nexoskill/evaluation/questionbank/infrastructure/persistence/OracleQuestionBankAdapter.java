package com.nexoskill.evaluation.questionbank.infrastructure.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexoskill.evaluation.organizations.application.TenantContextResolver;
import com.nexoskill.evaluation.organizations.domain.model.ContentScope;
import com.nexoskill.evaluation.questionbank.application.model.*;
import com.nexoskill.evaluation.questionbank.application.port.out.*;
import com.nexoskill.evaluation.questionbank.domain.model.*;
import com.nexoskill.evaluation.shared.domain.*;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Component;

@Component
public class OracleQuestionBankAdapter implements QuestionBankPort {
    private static final String INTERNAL_DIFFICULTY = "BASIC";

    private final SpringDataQuestionRepository questions;
    private final SpringDataQuestionTypeRepository types;
    private final SpringDataQuestionDifficultyRepository difficulties;
    private final SpringDataQuestionCategoryRepository categories;
    private final SpringDataQuestionMediaRepository media;
    private final QuestionUsageChecker usage;
    private final ObjectMapper json;
    private final Clock clock;
    private final TenantContextResolver tenantContextResolver;
    private final HttpServletRequest request;

    public OracleQuestionBankAdapter(
            SpringDataQuestionRepository questions,
            SpringDataQuestionTypeRepository types,
            SpringDataQuestionDifficultyRepository difficulties,
            SpringDataQuestionCategoryRepository categories,
            SpringDataQuestionMediaRepository media,
            QuestionUsageChecker usage,
            ObjectMapper json,
            Clock clock,
            TenantContextResolver tenantContextResolver,
            HttpServletRequest request) {
        this.questions = questions;
        this.types = types;
        this.difficulties = difficulties;
        this.categories = categories;
        this.media = media;
        this.usage = usage;
        this.json = json;
        this.clock = clock;
        this.tenantContextResolver = tenantContextResolver;
        this.request = request;
    }

    @Override
    public QuestionDetail create(CreateQuestionCommand command) {
        var selection = selection(command.typeCode(), command.categoryPublicIds(), Set.of());
        var settings = command.answerSettings();
        var entity = QuestionJpaEntity.create(
                UUID.randomUUID().toString(),
                selection.type(),
                internalDifficulty(),
                selection.categories(),
                trim(command.statement()),
                nullable(command.explanation()),
                optionalMedia(command.promptMediaPublicId()),
                javaLanguage(command.codeContent()),
                nullable(command.codeContent()),
                writeAnswers(settings.acceptedAnswers()),
                settings.caseSensitive(),
                selection.type().getCode().equals(QuestionTypeCode.OPEN_TEXT.name()),
                null,
                null,
                null,
                settings.maxLength(),
                command.actorUserId(),
                clock.instant());
        addOptions(entity, command.options());
        QuestionJpaEntity saved = questions.saveAndFlush(entity);
        return toDetail(saved, memberships(List.of(saved.getId())));
    }

    @Override
    public QuestionDetail update(UpdateQuestionCommand command) {
        var entity = locked(command.publicId());
        checkVersion(entity, command.expectedEntityVersion());
        if (entity.getStatus() == QuestionStatus.ARCHIVED) {
            throw error("QUESTION_ARCHIVED", "Reactiva la pregunta antes de editarla.");
        }
        if (entity.getStatus() == QuestionStatus.DELETED) {
            throw error("QUESTION_DELETED", "Restaura la pregunta antes de editarla.");
        }

        Set<String> existing = entity.getCategories().stream().map(QuestionCategoryJpaEntity::getPublicId)
                .collect(java.util.stream.Collectors.toSet());
        var selection = selection(command.typeCode(), command.categoryPublicIds(), existing);
        var settings = command.answerSettings();

        // Elimina primero las opciones administradas por Hibernate y fuerza el DELETE
        // antes de insertar las nuevas. Esto evita tanto el conflicto del índice
        // (QUESTION_ID, OPTION_ORDER) como el falso OptimisticLock causado por
        // borrar las mismas filas mediante bulk delete y orphanRemoval.
        entity.clearOptions();
        questions.flush();
        entity.apply(
                selection.type(),
                internalDifficulty(),
                selection.categories(),
                trim(command.statement()),
                nullable(command.explanation()),
                optionalMedia(command.promptMediaPublicId()),
                javaLanguage(command.codeContent()),
                nullable(command.codeContent()),
                writeAnswers(settings.acceptedAnswers()),
                settings.caseSensitive(),
                selection.type().getCode().equals(QuestionTypeCode.OPEN_TEXT.name()),
                null,
                null,
                null,
                settings.maxLength(),
                command.actorUserId(),
                clock.instant());
        addOptions(entity, command.options());
        QuestionJpaEntity saved = questions.saveAndFlush(entity);
        return toDetail(saved, memberships(List.of(saved.getId())));
    }

    @Override
    public QuestionDetail get(String id) {
        QuestionJpaEntity entity = find(id);
        return toDetail(entity, memberships(List.of(entity.getId())));
    }

    @Override
    public QuestionPage search(String query, QuestionStatus status, String type, String category,
            int page, int size) {
        String normalizedQuery = query == null || query.isBlank() ? null : query.trim().toLowerCase(Locale.ROOT);
        String normalizedType = norm(type);
        String normalizedCategory = category == null || category.isBlank() ? null
                : PublicIdNormalizer.requiredUuid(category, "QUESTION_CATEGORY_INVALID",
                        "La categoría indicada no es válida.");
        PageRequest pageable = PageRequest.of(page, size);
        Page<QuestionJpaEntity> result = normalizedQuery == null
                ? questions.searchWithoutText(status == null ? null : status.name(), normalizedType,
                        normalizedCategory, pageable)
                : questions.searchWithText(normalizedQuery, status == null ? null : status.name(), normalizedType,
                        normalizedCategory, pageable);
        MembershipIndex membershipIndex = memberships(result.getContent().stream().map(QuestionJpaEntity::getId).toList());
        return new QuestionPage(result.getContent().stream().map(entity -> toSummary(entity, membershipIndex)).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Override
    public QuestionDetail duplicate(String id, Long actor) {
        var source = find(id);
        if (source.getStatus() == QuestionStatus.DELETED) {
            throw error("QUESTION_DELETED", "No se puede duplicar una pregunta eliminada.");
        }
        var entity = QuestionJpaEntity.create(
                UUID.randomUUID().toString(),
                source.getType(),
                internalDifficulty(),
                new LinkedHashSet<>(source.getCategories()),
                source.getStatement() + " (copia)",
                source.getExplanation(),
                source.getPromptMedia(),
                javaLanguage(source.getCodeContent()),
                source.getCodeContent(),
                source.getAcceptedAnswersJson(),
                source.isCaseSensitive(),
                source.isManualReview(),
                null,
                null,
                null,
                source.getResponseMaxLength(),
                actor,
                clock.instant());
        for (var option : source.getOptions()) {
            entity.addOption(QuestionOptionJpaEntity.create(
                    entity,
                    UUID.randomUUID().toString(),
                    option.getOptionOrder(),
                    option.getText(),
                    option.getMedia(),
                    option.getMatchText(),
                    option.getMatchMedia(),
                    option.isCorrect(),
                    option.getFeedback(),
                    clock.instant()));
        }
        QuestionJpaEntity saved = questions.saveAndFlush(entity);
        return toDetail(saved, memberships(List.of(saved.getId())));
    }

    @Override
    public QuestionDetail changeStatus(String id, QuestionStatus status, long expected, Long actor) {
        var entity = locked(id);
        checkVersion(entity, expected);
        if (entity.getStatus() == QuestionStatus.DELETED) {
            throw error("QUESTION_DELETED", "Restaura la pregunta antes de cambiar su estado.");
        }
        if (status == QuestionStatus.ARCHIVED && usage.isUsedByActiveExam(entity.getId())) {
            throw error("QUESTION_USED_BY_ACTIVE_EXAM",
                    "No se puede archivar porque está incluida en una evaluación activa.");
        }
        entity.changeStatus(status, actor, clock.instant());
        QuestionJpaEntity saved = questions.saveAndFlush(entity);
        return toDetail(saved, memberships(List.of(saved.getId())));
    }

    @Override
    public QuestionDetail softDelete(String id, long expected, String reason, Long actor) {
        var entity = locked(id);
        checkVersion(entity, expected);
        if (entity.getStatus() == QuestionStatus.DELETED) {
            throw error("QUESTION_ALREADY_DELETED", "La pregunta ya está eliminada.");
        }
        if (usage.isUsedByActiveExam(entity.getId())) {
            throw error("QUESTION_USED_BY_ACTIVE_EXAM",
                    "No se puede eliminar porque está incluida en una evaluación activa.");
        }
        entity.softDelete(actor, clock.instant(), truncate(reason, 500));
        QuestionJpaEntity saved = questions.saveAndFlush(entity);
        return toDetail(saved, memberships(List.of(saved.getId())));
    }

    @Override
    public QuestionDetail restore(String id, long expected, Long actor) {
        var entity = locked(id);
        checkVersion(entity, expected);
        if (entity.getStatus() != QuestionStatus.DELETED) {
            throw error("QUESTION_NOT_DELETED", "La pregunta no está eliminada.");
        }
        entity.restore(actor, clock.instant());
        QuestionJpaEntity saved = questions.saveAndFlush(entity);
        return toDetail(saved, memberships(List.of(saved.getId())));
    }

    private void addOptions(QuestionJpaEntity entity, List<QuestionOptionCommand> commands) {
        int order = 1;
        for (var command : commands) {
            entity.addOption(QuestionOptionJpaEntity.create(
                    entity,
                    UUID.randomUUID().toString(),
                    order++,
                    nullable(command.text()),
                    optionalMedia(command.mediaPublicId()),
                    nullable(command.matchText()),
                    optionalMedia(command.matchMediaPublicId()),
                    command.correct(),
                    nullable(command.feedback()),
                    clock.instant()));
        }
    }

    private Selection selection(String type, List<String> ids, Set<String> existing) {
        var selectedType = types.findByCodeAndStatus(norm(type), CatalogStatus.ACTIVE)
                .orElseThrow(() -> error("QUESTION_TYPE_INVALID", "El tipo de pregunta no está disponible."));
        if (ids == null || ids.isEmpty()) {
            throw error("QUESTION_CATEGORY_REQUIRED", "Selecciona al menos una categoría.");
        }
        var tenant = tenantContextResolver.resolve(request);
        LinkedHashSet<QuestionCategoryJpaEntity> selectedCategories = new LinkedHashSet<>();
        for (String rawId : ids) {
            String id = PublicIdNormalizer.requiredUuid(rawId, "QUESTION_CATEGORY_INVALID",
                    "Una categoría seleccionada no es válida.");
            var category = categories.findByPublicId(id)
                    .orElseThrow(() -> error("CATEGORY_NOT_FOUND", "La categoría seleccionada no existe."));
            if (category.getContentScope() == ContentScope.ORGANIZATION
                    && !Objects.equals(category.getOwnerOrganizationId(), tenant.organizationId())) {
                throw error("CATEGORY_NOT_FOUND", "La categoría seleccionada no existe.");
            }
            if (category.getStatus() == CatalogStatus.DELETED) {
                throw error("CATEGORY_NOT_FOUND", "La categoría seleccionada no existe.");
            }
            if (category.getStatus() != CatalogStatus.ACTIVE && !existing.contains(id)) {
                throw error("CATEGORY_INACTIVE",
                        "La categoría se encuentra inactiva y no puede asignarse a nuevas preguntas.");
            }
            selectedCategories.add(category);
        }
        return new Selection(selectedType, selectedCategories);
    }

    private QuestionDifficultyJpaEntity internalDifficulty() {
        return difficulties.findById(INTERNAL_DIFFICULTY)
                .orElseThrow(() -> error("QUESTION_INTERNAL_DIFFICULTY_MISSING",
                        "No se encontró la clasificación interna requerida para guardar preguntas."));
    }

    private QuestionJpaEntity find(String id) {
        String normalized = PublicIdNormalizer.requiredUuid(id, "QUESTION_ID_INVALID",
                "La pregunta indicada no es válida.");
        return questions.findByPublicId(normalized)
                .orElseThrow(() -> error("QUESTION_NOT_FOUND", "La pregunta solicitada no existe."));
    }

    private QuestionJpaEntity locked(String id) {
        String normalized = PublicIdNormalizer.requiredUuid(id, "QUESTION_ID_INVALID",
                "La pregunta indicada no es válida.");
        return questions.findByPublicIdForUpdate(normalized)
                .orElseThrow(() -> error("QUESTION_NOT_FOUND", "La pregunta solicitada no existe."));
    }

    private void checkVersion(QuestionJpaEntity entity, long expected) {
        if (entity.getVersion() != expected) {
            throw error("QUESTION_CONCURRENT_MODIFICATION",
                    "La pregunta fue modificada por otra persona. Recarga la información antes de guardar.");
        }
    }

    private QuestionDetail toDetail(QuestionJpaEntity entity, MembershipIndex membershipIndex) {
        return new QuestionDetail(
                entity.getPublicId(),
                entity.getStatement(),
                entity.getExplanation(),
                entity.getType().getCode(),
                entity.getType().getName(),
                entity.getCategories().stream().map(this::catRef)
                        .sorted(Comparator.comparing(QuestionCategoryRef::name)).toList(),
                entity.getStatus(),
                entity.getVersion(),
                mediaView(entity.getPromptMedia()),
                entity.getCodeContent() == null ? null : "JAVA",
                entity.getCodeContent(),
                new QuestionAnswerSettings(readAnswers(entity.getAcceptedAnswersJson()), entity.isCaseSensitive(),
                        entity.isManualReview(), null, null, null, entity.getResponseMaxLength()),
                entity.getOptions().stream().map(option -> new QuestionOptionView(
                        option.getPublicId(),
                        option.getOptionOrder(),
                        option.getText(),
                        mediaView(option.getMedia()),
                        option.getMatchText(),
                        mediaView(option.getMatchMedia()),
                        option.isCorrect(),
                        option.getFeedback())).toList(),
                membershipIndex.forms(entity.getId()),
                membershipIndex.collections(entity.getId()),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private QuestionSummary toSummary(QuestionJpaEntity entity, MembershipIndex membershipIndex) {
        return new QuestionSummary(
                entity.getPublicId(),
                entity.getStatement(),
                entity.getType().getCode(),
                entity.getType().getName(),
                entity.getCategories().stream().map(this::catRef)
                        .sorted(Comparator.comparing(QuestionCategoryRef::name)).toList(),
                entity.getStatus(),
                entity.getPromptMedia() != null || entity.getOptions().stream()
                        .anyMatch(option -> option.getMedia() != null || option.getMatchMedia() != null),
                entity.getCodeContent() != null && !entity.getCodeContent().isBlank(),
                membershipIndex.forms(entity.getId()),
                membershipIndex.collections(entity.getId()),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private MembershipIndex memberships(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return MembershipIndex.empty();
        Map<Long, LinkedHashMap<String, QuestionUsageRef>> forms = new HashMap<>();
        Map<Long, LinkedHashMap<String, QuestionUsageRef>> collections = new HashMap<>();
        for (Object[] row : questions.findMemberships(ids)) {
            Long questionId = ((Number) row[0]).longValue();
            String formId = string(row[1]);
            String formTitle = string(row[2]);
            String collectionId = string(row[3]);
            String collectionName = string(row[4]);
            if (formId != null) {
                forms.computeIfAbsent(questionId, ignored -> new LinkedHashMap<>())
                        .putIfAbsent(formId, new QuestionUsageRef(formId, formTitle));
            }
            if (collectionId != null) {
                collections.computeIfAbsent(questionId, ignored -> new LinkedHashMap<>())
                        .putIfAbsent(collectionId, new QuestionUsageRef(collectionId, collectionName));
            }
        }
        return new MembershipIndex(forms, collections);
    }

    private String string(Object value) { return value == null ? null : value.toString(); }

    private QuestionCategoryRef catRef(QuestionCategoryJpaEntity category) {
        return new QuestionCategoryRef(category.getPublicId(), category.getCode(), category.getName(),
                category.getStatus());
    }

    private QuestionMediaView mediaView(QuestionMediaJpaEntity value) {
        return value == null ? null : new QuestionMediaView(value.getPublicId(), value.getOriginalName(),
                value.getContentType(), value.getSize(), "/api/v1/question-media/" + value.getPublicId());
    }

    private String norm(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private String javaLanguage(String codeContent) {
        return codeContent == null || codeContent.isBlank() ? null : "JAVA";
    }

    private String trim(String value) { return value == null ? null : value.trim(); }
    private String nullable(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private QuestionMediaJpaEntity optionalMedia(String id) {
        if (id == null || id.isBlank()) return null;
        String normalized = PublicIdNormalizer.requiredUuid(id, "QUESTION_MEDIA_INVALID",
                "La imagen indicada no es válida.");
        return media.findByPublicId(normalized)
                .orElseThrow(() -> error("QUESTION_MEDIA_NOT_FOUND", "La imagen indicada no existe."));
    }

    private String writeAnswers(List<String> answers) {
        try {
            return json.writeValueAsString(answers == null ? List.of() : answers);
        } catch (Exception exception) {
            throw error("QUESTION_ANSWERS_INVALID", "No fue posible procesar la configuración de respuesta.");
        }
    }

    private List<String> readAnswers(String value) {
        if (value == null || value.isBlank()) return List.of();
        try {
            return json.readValue(value, new TypeReference<List<String>>() { });
        } catch (Exception exception) {
            return List.of();
        }
    }

    private String truncate(String value, int max) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.length() <= max ? normalized : normalized.substring(0, max);
    }

    private BusinessException error(String code, String message) { return new BusinessException(code, message); }

    private record Selection(QuestionTypeJpaEntity type, LinkedHashSet<QuestionCategoryJpaEntity> categories) { }

    private record MembershipIndex(
            Map<Long, LinkedHashMap<String, QuestionUsageRef>> formMap,
            Map<Long, LinkedHashMap<String, QuestionUsageRef>> collectionMap) {
        static MembershipIndex empty() { return new MembershipIndex(Map.of(), Map.of()); }
        List<QuestionUsageRef> forms(Long id) {
            var values = formMap.get(id);
            return values == null ? List.of() : List.copyOf(values.values());
        }
        List<QuestionUsageRef> collections(Long id) {
            var values = collectionMap.get(id);
            return values == null ? List.of() : List.copyOf(values.values());
        }
    }
}
