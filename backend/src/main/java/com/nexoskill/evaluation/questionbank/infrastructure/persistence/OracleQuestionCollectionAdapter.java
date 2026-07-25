package com.nexoskill.evaluation.questionbank.infrastructure.persistence;

import com.nexoskill.evaluation.questionbank.application.model.*;
import com.nexoskill.evaluation.questionbank.application.port.out.QuestionCollectionPort;
import com.nexoskill.evaluation.questionbank.domain.model.*;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import com.nexoskill.evaluation.shared.domain.PublicIdNormalizer;
import java.text.Normalizer;
import java.time.Clock;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
public class OracleQuestionCollectionAdapter implements QuestionCollectionPort {
    private final SpringDataQuestionCollectionRepository collections;
    private final SpringDataQuestionCategoryRepository categories;
    private final SpringDataQuestionRepository questions;
    private final Clock clock;

    public OracleQuestionCollectionAdapter(SpringDataQuestionCollectionRepository collections,
            SpringDataQuestionCategoryRepository categories, SpringDataQuestionRepository questions, Clock clock) {
        this.collections = collections; this.categories = categories; this.questions = questions; this.clock = clock;
    }

    @Override public CollectionDetail create(CollectionCommands.Create command) {
        String name = name(command.name()); String normalized = normalize(name);
        if (collections.existsByNormalizedName(normalized)) throw error("COLLECTION_ALREADY_EXISTS",
                "Ya existe una colección con ese nombre.");
        var entity = QuestionCollectionJpaEntity.create(UUID.randomUUID().toString(), name, normalized,
                nullable(command.description()), resolveCategories(command.categoryPublicIds(), Set.of()),
                resolveQuestions(command.questionPublicIds()), command.actorUserId(), clock.instant());
        return detail(collections.saveAndFlush(entity));
    }

    @Override public CollectionDetail update(CollectionCommands.Update command) {
        String id = PublicIdNormalizer.requiredUuid(command.publicId(), "COLLECTION_ID_INVALID",
                "La colección indicada no es válida.");
        var entity = collections.findForUpdate(id).orElseThrow(() -> error("COLLECTION_NOT_FOUND",
                "La colección solicitada no existe."));
        if (entity.getVersion() != command.expectedEntityVersion()) throw error("COLLECTION_CONCURRENT_MODIFICATION",
                "La colección fue modificada por otra persona.");
        String name = name(command.name()); String normalized = normalize(name);
        if (collections.existsByNormalizedNameAndPublicIdNot(normalized, id)) throw error("COLLECTION_ALREADY_EXISTS",
                "Ya existe una colección con ese nombre.");
        Set<String> retained = entity.getCategories().stream().map(QuestionCategoryJpaEntity::getPublicId)
                .collect(Collectors.toSet());
        entity.apply(name, normalized, nullable(command.description()),
                resolveCategories(command.categoryPublicIds(), retained),
                resolveQuestions(command.questionPublicIds()), command.actorUserId(), clock.instant());
        return detail(collections.saveAndFlush(entity));
    }

    @Override public CollectionDetail get(String id) {
        String publicId = PublicIdNormalizer.requiredUuid(id, "COLLECTION_ID_INVALID",
                "La colección indicada no es válida.");
        return detail(collections.findByPublicId(publicId).orElseThrow(() -> error("COLLECTION_NOT_FOUND",
                "La colección solicitada no existe.")));
    }

    @Override public CollectionPage search(String query, String status, int page, int size) {
        String normalizedQuery = query == null || query.isBlank() ? null : query.trim().toLowerCase(Locale.ROOT);
        String normalizedStatus = status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
        Page<QuestionCollectionJpaEntity> result = collections.search(normalizedQuery, normalizedStatus,
                PageRequest.of(page, size));
        return new CollectionPage(result.getContent().stream().map(this::summary).toList(), result.getNumber(),
                result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Override public CollectionDetail changeStatus(CollectionCommands.ChangeStatus command) {
        String id = PublicIdNormalizer.requiredUuid(command.publicId(), "COLLECTION_ID_INVALID",
                "La colección indicada no es válida.");
        var entity = collections.findForUpdate(id).orElseThrow(() -> error("COLLECTION_NOT_FOUND",
                "La colección solicitada no existe."));
        if (entity.getVersion() != command.expectedEntityVersion()) throw error("COLLECTION_CONCURRENT_MODIFICATION",
                "La colección fue modificada por otra persona. Recarga la información.");
        entity.changeStatus(command.status(), command.actorUserId(), clock.instant());
        return detail(collections.saveAndFlush(entity));
    }

    private Set<QuestionCategoryJpaEntity> resolveCategories(List<String> ids, Set<String> existing) {
        Set<String> canonical = ids.stream().map(value -> PublicIdNormalizer.requiredUuid(value,
                "CATEGORY_ID_INVALID", "Una categoría no es válida."))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        var found = categories.findAllByPublicIdIn(canonical);
        if (found.size() != canonical.size()) throw error("CATEGORY_NOT_FOUND", "Una categoría seleccionada no existe.");
        for (var category : found) if (category.getStatus() != CatalogStatus.ACTIVE
                && !existing.contains(category.getPublicId())) throw error("CATEGORY_INACTIVE",
                        "La categoría " + category.getName() + " está inactiva.");
        return new LinkedHashSet<>(found);
    }

    private Set<QuestionJpaEntity> resolveQuestions(List<String> ids) {
        Set<String> canonical = ids.stream().map(value -> PublicIdNormalizer.requiredUuid(value,
                "QUESTION_ID_INVALID", "Una pregunta no es válida."))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        var found = questions.findAllByPublicIdIn(canonical);
        if (found.size() != canonical.size()) throw error("QUESTION_NOT_FOUND", "Una pregunta seleccionada no existe.");
        for (var question : found) {
            if (question.getStatus() == QuestionStatus.DELETED) throw error("QUESTION_DELETED",
                    "No se pueden agregar preguntas eliminadas.");
            if (question.getStatus() != QuestionStatus.ACTIVE) throw error("QUESTION_ARCHIVED",
                    "No se pueden agregar preguntas archivadas.");
        }
        return new LinkedHashSet<>(found);
    }

    private List<QuestionJpaEntity> effective(QuestionCollectionJpaEntity entity) {
        LinkedHashMap<Long, QuestionJpaEntity> result = new LinkedHashMap<>();
        for (var question : entity.getQuestions()) if (question.getStatus() == QuestionStatus.ACTIVE)
            result.put(question.getId(), question);
        Set<Long> categoryIds = entity.getCategories().stream().map(QuestionCategoryJpaEntity::getId)
                .collect(Collectors.toSet());
        if (!categoryIds.isEmpty()) for (var question : questions.findByStatusAndAnyCategoryIdIn(
                QuestionStatus.ACTIVE.name(), categoryIds)) result.put(question.getId(), question);
        return result.values().stream().sorted(Comparator.comparing(QuestionJpaEntity::getStatement,
                String.CASE_INSENSITIVE_ORDER)).toList();
    }

    private CollectionSummary summary(QuestionCollectionJpaEntity entity) {
        return new CollectionSummary(entity.getPublicId(), entity.getName(), entity.getDescription(),
                entity.getStatus(), entity.getCategories().size(), visibleExplicit(entity).size(), effective(entity).size(),
                entity.getVersion(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private CollectionDetail detail(QuestionCollectionJpaEntity entity) {
        return new CollectionDetail(entity.getPublicId(), entity.getName(), entity.getDescription(), entity.getStatus(),
                entity.getCategories().stream().map(category -> new QuestionCategoryRef(category.getPublicId(),
                        category.getCode(), category.getName(), category.getStatus()))
                        .sorted(Comparator.comparing(QuestionCategoryRef::name)).toList(),
                visibleExplicit(entity).stream().map(this::questionSummary).toList(),
                effective(entity).stream().map(this::questionSummary).toList(), entity.getVersion(), entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private List<QuestionJpaEntity> visibleExplicit(QuestionCollectionJpaEntity entity) {
        return entity.getQuestions().stream().filter(question -> question.getStatus() != QuestionStatus.DELETED)
                .sorted(Comparator.comparing(QuestionJpaEntity::getStatement, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    private QuestionSummary questionSummary(QuestionJpaEntity question) {
        return new QuestionSummary(question.getPublicId(), question.getStatement(), question.getType().getCode(),
                question.getType().getName(),
                question.getCategories().stream().map(category -> new QuestionCategoryRef(category.getPublicId(),
                        category.getCode(), category.getName(), category.getStatus())).toList(), question.getStatus(),
                question.getPromptMedia() != null || question.getOptions().stream()
                        .anyMatch(option -> option.getMedia() != null || option.getMatchMedia() != null),
                question.getCodeContent() != null && !question.getCodeContent().isBlank(),
                java.util.List.of(), java.util.List.of(), question.getCreatedAt(), question.getUpdatedAt());
    }

    private String name(String value) {
        if (value == null || value.isBlank()) throw error("COLLECTION_NAME_REQUIRED", "El nombre es obligatorio.");
        return value.trim().replaceAll("\\s+", " ");
    }
    private String normalize(String value) { return Normalizer.normalize(value, Normalizer.Form.NFKC)
            .replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT); }
    private String nullable(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private BusinessException error(String code, String message) { return new BusinessException(code, message); }
}
