package com.nexoskill.evaluation.forms.application;

import com.nexoskill.evaluation.forms.domain.FormContentMode;
import com.nexoskill.evaluation.forms.infrastructure.FormQuestionJpaEntity;
import com.nexoskill.evaluation.forms.infrastructure.FormQuestionPoolJpaEntity;
import com.nexoskill.evaluation.forms.infrastructure.FormSectionJpaEntity;
import com.nexoskill.evaluation.forms.infrastructure.FormSectionRepository;
import com.nexoskill.evaluation.globalcontent.application.service.GlobalContentAccessPolicy;
import com.nexoskill.evaluation.globalcontent.domain.model.GlobalContentType;
import com.nexoskill.evaluation.organizations.domain.model.ContentScope;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationRepository;
import com.nexoskill.evaluation.questionbank.domain.model.CatalogStatus;
import com.nexoskill.evaluation.questionbank.domain.model.QuestionStatus;
import com.nexoskill.evaluation.questionbank.infrastructure.persistence.QuestionCategoryJpaEntity;
import com.nexoskill.evaluation.questionbank.infrastructure.persistence.QuestionJpaEntity;
import com.nexoskill.evaluation.questionbank.infrastructure.persistence.SpringDataQuestionCategoryRepository;
import com.nexoskill.evaluation.questionbank.infrastructure.persistence.SpringDataQuestionRepository;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
public class FormContentManager {
    private static final Set<String> ALLOWED_DIFFICULTIES = Set.of("JR", "STD", "SR");

    private final FormSectionRepository sections;
    private final SpringDataQuestionRepository questions;
    private final SpringDataQuestionCategoryRepository categories;
    private final OrganizationRepository organizations;
    private final GlobalContentAccessPolicy accessPolicy;

    public FormContentManager(FormSectionRepository sections,
            SpringDataQuestionRepository questions,
            SpringDataQuestionCategoryRepository categories,
            OrganizationRepository organizations,
            GlobalContentAccessPolicy accessPolicy) {
        this.sections = sections;
        this.questions = questions;
        this.categories = categories;
        this.organizations = organizations;
        this.accessPolicy = accessPolicy;
    }

    public Counts counts(Long formId) {
        List<FormSectionJpaEntity> storedSections = sections.findAllByFormIdOrderBySectionOrderAsc(formId);
        int questionCount = storedSections.stream().mapToInt(section -> section.getQuestions().size()).sum();
        int poolCount = storedSections.stream().mapToInt(section -> section.getPools().size()).sum();
        return new Counts(questionCount, poolCount);
    }

    public Snapshot load(Long formId, FormCreationTargetResolver.Target target) {
        List<FormSectionJpaEntity> storedSections = sections.findAllByFormIdOrderBySectionOrderAsc(formId);
        List<FormQuestionJpaEntity> questionLinks = storedSections.stream()
                .flatMap(section -> section.getQuestions().stream())
                .sorted(Comparator.comparing(FormQuestionJpaEntity::getQuestionOrder))
                .toList();
        Map<Long, QuestionJpaEntity> questionById = questions.findAllById(
                        questionLinks.stream().map(FormQuestionJpaEntity::getQuestionId).distinct().toList())
                .stream().collect(Collectors.toMap(QuestionJpaEntity::getId, Function.identity()));
        List<FormModels.QuestionView> questionViews = new ArrayList<>();
        int questionOrder = 1;
        for (FormQuestionJpaEntity link : questionLinks) {
            QuestionJpaEntity question = questionById.get(link.getQuestionId());
            if (question == null) continue;
            questionViews.add(toQuestionView(question, questionOrder++, link.getPoints(), link.isRequired()));
        }

        List<FormQuestionPoolJpaEntity> allPoolLinks = storedSections.stream()
                .flatMap(section -> section.getPools().stream())
                .toList();
        boolean hasUnsupportedLegacyPool = allPoolLinks.stream()
                .anyMatch(pool -> !"CATEGORY".equals(pool.getSourceType()));
        if (hasUnsupportedLegacyPool) {
            throw new BusinessException("FORM_LEGACY_POOL_UNSUPPORTED",
                    "El formulario contiene un Pool heredado basado en Colecciones. "
                            + "Ese contenido no puede editarse desde este flujo sin una migración explícita.");
        }
        List<FormQuestionPoolJpaEntity> poolLinks = allPoolLinks.stream()
                .sorted(Comparator.comparing(FormQuestionPoolJpaEntity::getPoolOrder))
                .toList();
        Map<Long, QuestionCategoryJpaEntity> categoryById = categories.findAllById(
                        poolLinks.stream().map(FormQuestionPoolJpaEntity::getCategoryId).distinct().toList())
                .stream().collect(Collectors.toMap(QuestionCategoryJpaEntity::getId, Function.identity()));
        List<FormModels.PoolView> poolViews = new ArrayList<>();
        int poolOrder = 1;
        for (FormQuestionPoolJpaEntity link : poolLinks) {
            QuestionCategoryJpaEntity category = categoryById.get(link.getCategoryId());
            if (category == null) continue;
            poolViews.add(new FormModels.PoolView(link.getPublicId(), category.getPublicId(), category.getName(),
                    link.getQuestionCount(), link.getDifficultyCode(), poolOrder++,
                    activeQuestionCount(category, target, link.getDifficultyCode())));
        }
        return new Snapshot(questionViews, poolViews,
                questionViews.stream().map(FormModels.QuestionView::questionPublicId).collect(Collectors.toSet()));
    }

    public void replace(Long formId, FormContentMode mode, List<ResolvedQuestion> resolvedQuestions,
            List<ResolvedPool> resolvedPools) {
        List<FormSectionJpaEntity> current = sections.findAllByFormIdOrderBySectionOrderAsc(formId);
        if (!current.isEmpty()) {
            sections.deleteAll(current);
            sections.flush();
        }
        FormSectionJpaEntity section = FormSectionJpaEntity.content(formId, UUID.randomUUID().toString());
        if (mode == FormContentMode.MANUAL) {
            int order = 1;
            for (ResolvedQuestion item : resolvedQuestions) {
                section.addQuestion(item.question().getId(), order++, item.points(), item.required());
            }
        } else {
            int order = 1;
            for (ResolvedPool item : resolvedPools) {
                section.addPool(UUID.randomUUID().toString(), item.category().getId(), item.questionCount(),
                        item.difficultyCode(), order++);
            }
        }
        sections.saveAndFlush(section);
    }

    public ResolvedContent validate(FormContentMode mode, List<FormModels.QuestionItem> questionItems,
            List<FormModels.PoolItem> poolItems, FormCreationTargetResolver.Target target,
            Set<String> existingQuestionPublicIds) {
        List<FormModels.QuestionItem> safeQuestions = questionItems == null ? List.of() : questionItems;
        List<FormModels.PoolItem> safePools = poolItems == null ? List.of() : poolItems;
        if (mode == FormContentMode.MANUAL) {
            if (safeQuestions.isEmpty()) {
                throw new BusinessException("FORM_QUESTIONS_REQUIRED",
                        "Agrega al menos una pregunta activa al formulario.", Map.of("questions", "Agrega al menos una pregunta."));
            }
            if (!safePools.isEmpty()) {
                throw new BusinessException("FORM_CONTENT_MIXED",
                        "Un formulario manual no puede conservar una configuración de Pool aleatorio.");
            }
            return new ResolvedContent(resolveQuestions(safeQuestions, target, existingQuestionPublicIds), List.of());
        }
        if (!safeQuestions.isEmpty()) {
            throw new BusinessException("FORM_CONTENT_MIXED",
                    "Un formulario con Pool aleatorio no puede conservar preguntas manuales.");
        }
        if (safePools.isEmpty()) {
            throw new BusinessException("FORM_POOLS_REQUIRED",
                    "Configura al menos una categoría para el Pool aleatorio.", Map.of("pools", "Selecciona al menos una categoría."));
        }
        return new ResolvedContent(List.of(), resolvePools(safePools, target));
    }

    public FormModels.QuestionOptionPage questionOptions(FormCreationTargetResolver.Target target,
            String query, String categoryPublicId, int page, int size) {
        if (categoryPublicId != null && !categoryPublicId.isBlank()) {
            QuestionCategoryJpaEntity category = category(categoryPublicId);
            assertActiveCategoryCompatible(category, target);
        }
        String normalizedQuery = query == null || query.isBlank()
                ? null : "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
        var result = questions.findActiveForForm(target.scope() == ContentScope.GLOBAL ? 1 : 0,
                target.organizationId(), normalizedQuery,
                categoryPublicId == null || categoryPublicId.isBlank() ? null : canonical(categoryPublicId),
                PageRequest.of(Math.max(0, page), Math.min(Math.max(size, 1), 100)));
        return new FormModels.QuestionOptionPage(result.getContent().stream()
                .map(this::toQuestionOption).toList(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    public List<FormModels.CategoryOptionView> categoryOptions(FormCreationTargetResolver.Target target) {
        return categories.findAllByStatusOrderByNameAsc(CatalogStatus.ACTIVE).stream()
                .filter(category -> categoryCompatible(category, target))
                .map(category -> new FormModels.CategoryOptionView(category.getPublicId(), category.getCode(),
                        category.getName(), category.getContentScope().name(),
                        ownerName(category.getOwnerOrganizationId()), activeQuestionCount(category, target, null)))
                .sorted(Comparator.comparing(FormModels.CategoryOptionView::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private List<ResolvedQuestion> resolveQuestions(List<FormModels.QuestionItem> items,
            FormCreationTargetResolver.Target target, Set<String> existingQuestionPublicIds) {
        LinkedHashMap<String, FormModels.QuestionItem> unique = new LinkedHashMap<>();
        for (FormModels.QuestionItem item : items.stream()
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(item -> item.order() == null ? Integer.MAX_VALUE : item.order()))
                .toList()) {
            if (item == null || item.questionPublicId() == null || item.questionPublicId().isBlank()) {
                throw new BusinessException("FORM_QUESTION_INVALID", "Una de las preguntas seleccionadas no es válida.");
            }
            String publicId = canonical(item.questionPublicId());
            if (unique.putIfAbsent(publicId, item) != null) {
                throw new BusinessException("FORM_QUESTION_DUPLICATED",
                        "Una pregunta no puede agregarse más de una vez al mismo formulario.");
            }
        }
        Map<String, QuestionJpaEntity> byPublicId = questions.findAllByPublicIdIn(unique.keySet()).stream()
                .collect(Collectors.toMap(QuestionJpaEntity::getPublicId, Function.identity()));
        if (byPublicId.size() != unique.size()) {
            throw new BusinessException("FORM_QUESTION_NOT_FOUND",
                    "Una de las preguntas seleccionadas ya no existe.");
        }
        List<ResolvedQuestion> result = new ArrayList<>();
        for (Map.Entry<String, FormModels.QuestionItem> entry : unique.entrySet()) {
            QuestionJpaEntity question = byPublicId.get(entry.getKey());
            boolean retained = existingQuestionPublicIds.contains(entry.getKey());
            if (question.getStatus() == QuestionStatus.DELETED
                    || (question.getStatus() != QuestionStatus.ACTIVE && !retained)) {
                throw new BusinessException("FORM_QUESTION_INACTIVE",
                        "Solo pueden agregarse preguntas activas a un formulario.");
            }
            if (!questionCompatible(question, target)) {
                throw new BusinessException("FORM_QUESTION_SCOPE_INVALID",
                        "Una pregunta seleccionada no está disponible para el alcance del formulario.");
            }
            BigDecimal points = entry.getValue().points() == null ? BigDecimal.ONE : entry.getValue().points();
            if (points.signum() <= 0) {
                throw new BusinessException("FORM_QUESTION_POINTS_INVALID",
                        "El puntaje de cada pregunta debe ser mayor que cero.");
            }
            result.add(new ResolvedQuestion(question, points, entry.getValue().required()));
        }
        return result;
    }

    private List<ResolvedPool> resolvePools(List<FormModels.PoolItem> items,
            FormCreationTargetResolver.Target target) {
        Set<String> seen = new HashSet<>();
        List<ResolvedPool> result = new ArrayList<>();
        for (FormModels.PoolItem item : items.stream()
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(item -> item.order() == null ? Integer.MAX_VALUE : item.order()))
                .toList()) {
            if (item == null || item.sourcePublicId() == null || item.sourcePublicId().isBlank()
                    || (item.sourceType() != null && !"CATEGORY".equalsIgnoreCase(item.sourceType()))) {
                throw new BusinessException("FORM_POOL_SOURCE_INVALID",
                        "Los Pools aleatorios solo pueden configurarse mediante categorías.");
            }
            String publicId = canonical(item.sourcePublicId());
            if (!seen.add(publicId)) {
                throw new BusinessException("FORM_POOL_CATEGORY_DUPLICATED",
                        "Una categoría no puede agregarse más de una vez al Pool aleatorio.");
            }
            QuestionCategoryJpaEntity category = category(publicId);
            assertActiveCategoryCompatible(category, target);
            int count = item.questionCount() == null ? 0 : item.questionCount();
            if (count <= 0) {
                throw new BusinessException("FORM_POOL_COUNT_INVALID",
                        "Indica cuántas preguntas debe seleccionar el Pool para cada categoría.");
            }
            String difficulty = normalizeDifficulty(item.difficultyCode());
            int available = activeQuestionCount(category, target, difficulty);
            if (available < count) {
                throw new BusinessException("FORM_POOL_INSUFFICIENT_QUESTIONS",
                        "La categoría " + category.getName() + " solo tiene " + available
                                + " preguntas activas disponibles para este alcance.");
            }
            result.add(new ResolvedPool(category, count, difficulty));
        }
        return result;
    }

    private void assertActiveCategoryCompatible(QuestionCategoryJpaEntity category,
            FormCreationTargetResolver.Target target) {
        if (category.getStatus() != CatalogStatus.ACTIVE) {
            throw new BusinessException("FORM_POOL_CATEGORY_INACTIVE",
                    "El Pool solo puede utilizar categorías activas.");
        }
        if (!categoryCompatible(category, target)) {
            throw new BusinessException("FORM_POOL_CATEGORY_SCOPE_INVALID",
                    "Una categoría seleccionada no está disponible para el alcance del formulario.");
        }
    }

    private boolean questionCompatible(QuestionJpaEntity question, FormCreationTargetResolver.Target target) {
        if (target.scope() == ContentScope.GLOBAL) {
            return question.getContentScope() == ContentScope.GLOBAL;
        }
        return accessPolicy.canRead(GlobalContentType.QUESTION, question.getId(), question.getContentScope(),
                question.getOwnerOrganizationId(), target.asReadContext());
    }

    private boolean categoryCompatible(QuestionCategoryJpaEntity category,
            FormCreationTargetResolver.Target target) {
        if (target.scope() == ContentScope.GLOBAL) {
            return category.getContentScope() == ContentScope.GLOBAL;
        }
        return accessPolicy.canRead(GlobalContentType.CATEGORY, category.getId(), category.getContentScope(),
                category.getOwnerOrganizationId(), target.asReadContext());
    }

    private int activeQuestionCount(QuestionCategoryJpaEntity category,
            FormCreationTargetResolver.Target target, String difficultyCode) {
        return (int) questions.findByStatusAndAnyCategoryIdIn(QuestionStatus.ACTIVE.name(), List.of(category.getId()))
                .stream()
                .filter(question -> questionCompatible(question, target))
                .filter(question -> difficultyCode == null
                        || (question.getDifficulty() != null
                        && difficultyCode.equalsIgnoreCase(question.getDifficulty().getCode())))
                .count();
    }

    private FormModels.QuestionView toQuestionView(QuestionJpaEntity question, int order,
            BigDecimal points, boolean required) {
        return new FormModels.QuestionView(question.getPublicId(), question.getStatement(),
                question.getType().getCode(), question.getType().getName(),
                question.getDifficulty() == null ? null : question.getDifficulty().getCode(),
                question.getDifficulty() == null ? null : question.getDifficulty().getName(),
                question.getTechnology() == null ? null : question.getTechnology().getName(),
                question.getLevelCode(), categoryNames(question), question.getStatus().name(),
                order, points, required);
    }

    private FormModels.QuestionOptionView toQuestionOption(QuestionJpaEntity question) {
        List<FormModels.QuestionAnswerOption> answerOptions = question.getOptions().stream()
                .map(option -> new FormModels.QuestionAnswerOption(option.getPublicId(), option.getOptionOrder(),
                        option.getText(), option.getMatchText(), option.isCorrect(), option.getFeedback()))
                .toList();
        return new FormModels.QuestionOptionView(question.getPublicId(), question.getStatement(),
                question.getType().getCode(), question.getType().getName(),
                question.getDifficulty() == null ? null : question.getDifficulty().getCode(),
                question.getDifficulty() == null ? null : question.getDifficulty().getName(),
                question.getTechnology() == null ? null : question.getTechnology().getName(),
                question.getLevelCode(), categoryNames(question), question.getContentScope().name(),
                ownerName(question.getOwnerOrganizationId()), question.getExplanation(), question.getCodeLanguage(),
                question.getCodeContent(), question.getAcceptedAnswersJson(), answerOptions, BigDecimal.ONE);
    }

    private List<String> categoryNames(QuestionJpaEntity question) {
        return question.getCategories().stream().map(QuestionCategoryJpaEntity::getName)
                .sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    private QuestionCategoryJpaEntity category(String publicId) {
        return categories.findByPublicId(canonical(publicId))
                .orElseThrow(() -> new BusinessException("FORM_POOL_CATEGORY_NOT_FOUND",
                        "Una de las categorías seleccionadas ya no existe."));
    }

    private String ownerName(Long organizationId) {
        return organizations.findById(organizationId).map(value -> value.isGlobal() ? "GLOBAL" : value.getName())
                .orElse("Organización no disponible");
    }

    private static String normalizeDifficulty(String value) {
        if (value == null || value.isBlank() || "ALL".equalsIgnoreCase(value.trim())) return null;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_DIFFICULTIES.contains(normalized)) {
            throw new BusinessException("FORM_POOL_DIFFICULTY_INVALID",
                    "La dificultad del Pool debe ser JR, STD o SR.");
        }
        return normalized;
    }

    private static String canonical(String value) {
        try {
            return UUID.fromString(value.trim()).toString();
        } catch (Exception exception) {
            throw new BusinessException("FORM_CONTENT_ID_INVALID",
                    "Uno de los elementos seleccionados no tiene un identificador válido.");
        }
    }

    public record Counts(int questionCount, int poolCount) {}

    public record Snapshot(List<FormModels.QuestionView> questions, List<FormModels.PoolView> pools,
            Set<String> questionPublicIds) {
        public Snapshot {
            questions = List.copyOf(questions);
            pools = List.copyOf(pools);
            questionPublicIds = Set.copyOf(questionPublicIds);
        }
    }

    public record ResolvedQuestion(QuestionJpaEntity question, BigDecimal points, boolean required) {}
    public record ResolvedPool(QuestionCategoryJpaEntity category, int questionCount, String difficultyCode) {}
    public record ResolvedContent(List<ResolvedQuestion> questions, List<ResolvedPool> pools) {}
}
