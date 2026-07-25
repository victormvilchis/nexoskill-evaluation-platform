package com.nexoskill.evaluation.questionbank.infrastructure.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexoskill.evaluation.questionbank.application.model.*;
import com.nexoskill.evaluation.questionbank.application.port.out.*;
import com.nexoskill.evaluation.questionbank.domain.model.*;
import com.nexoskill.evaluation.shared.domain.*;
import java.time.Clock;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Component;

@Component
public class OracleQuestionBankAdapter implements QuestionBankPort {
    private final SpringDataQuestionRepository questions;
    private final SpringDataQuestionOptionRepository options;
    private final SpringDataQuestionTypeRepository types;
    private final SpringDataQuestionDifficultyRepository difficulties;
    private final SpringDataQuestionCategoryRepository categories;
    private final SpringDataQuestionMediaRepository media;
    private final QuestionUsageChecker usage;
    private final ObjectMapper json;
    private final Clock clock;

    public OracleQuestionBankAdapter(SpringDataQuestionRepository questions,
            SpringDataQuestionOptionRepository options,
            SpringDataQuestionTypeRepository types,
            SpringDataQuestionDifficultyRepository difficulties,
            SpringDataQuestionCategoryRepository categories,
            SpringDataQuestionMediaRepository media,
            QuestionUsageChecker usage,
            ObjectMapper json,
            Clock clock) {
        this.questions = questions;
        this.options = options;
        this.types = types;
        this.difficulties = difficulties;
        this.categories = categories;
        this.media = media;
        this.usage = usage;
        this.json = json;
        this.clock = clock;
    }

    @Override
    public QuestionDetail create(CreateQuestionCommand command) {
        var selection = selection(command.typeCode(), command.difficultyCode(), command.categoryPublicIds(), Set.of());
        var settings = command.answerSettings();
        var entity = QuestionJpaEntity.create(UUID.randomUUID().toString(), selection.type(), selection.difficulty(),
                selection.categories(), trim(command.statement()), nullable(command.explanation()),
                optionalMedia(command.promptMediaPublicId()), code(command.codeLanguage()),
                nullable(command.codeContent()), writeAnswers(settings.acceptedAnswers()), settings.caseSensitive(),
                effectiveManual(selection.type().getCode(), settings.manualReview()), settings.numericMin(),
                settings.numericMax(), settings.numericTolerance(), settings.maxLength(), command.actorUserId(),
                clock.instant());
        addOptions(entity, command.options());
        return toDetail(questions.saveAndFlush(entity));
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
        var selection = selection(command.typeCode(), command.difficultyCode(), command.categoryPublicIds(), existing);
        var settings = command.answerSettings();

        options.deleteByQuestionId(entity.getId());
        options.flush();
        entity.clearOptions();
        entity.apply(selection.type(), selection.difficulty(), selection.categories(), trim(command.statement()),
                nullable(command.explanation()), optionalMedia(command.promptMediaPublicId()),
                code(command.codeLanguage()), nullable(command.codeContent()), writeAnswers(settings.acceptedAnswers()),
                settings.caseSensitive(), effectiveManual(selection.type().getCode(), settings.manualReview()),
                settings.numericMin(), settings.numericMax(), settings.numericTolerance(), settings.maxLength(),
                command.actorUserId(), clock.instant());
        addOptions(entity, command.options());
        return toDetail(questions.saveAndFlush(entity));
    }

    @Override
    public QuestionDetail get(String id) {
        return toDetail(find(id));
    }

    @Override
    public QuestionPage search(String query, QuestionStatus status, String type, String difficulty, String category,
            int page, int size) {
        String normalizedQuery = query == null || query.isBlank() ? null : query.trim().toLowerCase(Locale.ROOT);
        String normalizedType = norm(type);
        String normalizedDifficulty = norm(difficulty);
        String normalizedCategory = category == null || category.isBlank() ? null
                : PublicIdNormalizer.requiredUuid(category, "QUESTION_CATEGORY_INVALID",
                        "La categoría indicada no es válida.");
        PageRequest pageable = PageRequest.of(page, size);
        Page<QuestionJpaEntity> result = normalizedQuery == null
                ? questions.searchWithoutText(status == null ? null : status.name(), normalizedType,
                        normalizedDifficulty, normalizedCategory, pageable)
                : questions.searchWithText(normalizedQuery, status == null ? null : status.name(), normalizedType,
                        normalizedDifficulty, normalizedCategory, pageable);
        return new QuestionPage(result.getContent().stream().map(this::toSummary).toList(), result.getNumber(),
                result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Override
    public QuestionDetail duplicate(String id, Long actor) {
        var source = find(id);
        if (source.getStatus() == QuestionStatus.DELETED) {
            throw error("QUESTION_DELETED", "No se puede duplicar una pregunta eliminada.");
        }
        var entity = QuestionJpaEntity.create(UUID.randomUUID().toString(), source.getType(), source.getDifficulty(),
                new LinkedHashSet<>(source.getCategories()), source.getStatement() + " (copia)", source.getExplanation(),
                source.getPromptMedia(), source.getCodeLanguage(), source.getCodeContent(),
                source.getAcceptedAnswersJson(), source.isCaseSensitive(), source.isManualReview(),
                source.getNumericMin(), source.getNumericMax(), source.getNumericTolerance(),
                source.getResponseMaxLength(), actor, clock.instant());
        for (var option : source.getOptions()) {
            entity.addOption(QuestionOptionJpaEntity.create(entity, UUID.randomUUID().toString(),
                    option.getOptionOrder(), option.getText(), option.getMedia(), option.isCorrect(), clock.instant()));
        }
        return toDetail(questions.saveAndFlush(entity));
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
        return toDetail(questions.saveAndFlush(entity));
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
        return toDetail(questions.saveAndFlush(entity));
    }

    @Override
    public QuestionDetail restore(String id, long expected, Long actor) {
        var entity = locked(id);
        checkVersion(entity, expected);
        if (entity.getStatus() != QuestionStatus.DELETED) {
            throw error("QUESTION_NOT_DELETED", "La pregunta no está eliminada.");
        }
        entity.restore(actor, clock.instant());
        return toDetail(questions.saveAndFlush(entity));
    }

    private void addOptions(QuestionJpaEntity entity, List<QuestionOptionCommand> commands) {
        int order = 1;
        for (var command : commands) {
            entity.addOption(QuestionOptionJpaEntity.create(entity, UUID.randomUUID().toString(), order++,
                    nullable(command.text()), optionalMedia(command.mediaPublicId()), command.correct(),
                    clock.instant()));
        }
    }

    private Selection selection(String type, String difficulty, List<String> ids, Set<String> existing) {
        var selectedType = types.findByCodeAndStatus(norm(type), CatalogStatus.ACTIVE)
                .orElseThrow(() -> error("QUESTION_TYPE_INVALID", "El tipo de pregunta no está disponible."));
        var selectedDifficulty = difficulties.findByCodeAndStatus(norm(difficulty), CatalogStatus.ACTIVE)
                .orElseThrow(() -> error("QUESTION_DIFFICULTY_INVALID", "La dificultad no está disponible."));
        Set<String> canonical = ids.stream()
                .map(value -> PublicIdNormalizer.requiredUuid(value, "QUESTION_CATEGORY_INVALID",
                        "Una categoría no es válida."))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (canonical.isEmpty()) {
            throw error("QUESTION_CATEGORY_REQUIRED", "Selecciona al menos una categoría.");
        }
        List<QuestionCategoryJpaEntity> found = categories.findAllByPublicIdIn(canonical);
        if (found.size() != canonical.size()) {
            throw error("CATEGORY_NOT_FOUND", "Una de las categorías seleccionadas no existe.");
        }
        for (var category : found) {
            if (category.getStatus() != CatalogStatus.ACTIVE && !existing.contains(category.getPublicId())) {
                throw error("CATEGORY_INACTIVE", "La categoría " + category.getName() + " está inactiva.");
            }
        }
        return new Selection(selectedType, selectedDifficulty, new LinkedHashSet<>(found));
    }

    private QuestionJpaEntity find(String id) {
        String publicId = PublicIdNormalizer.requiredUuid(id, "QUESTION_ID_INVALID",
                "La pregunta indicada no es válida.");
        return questions.findByPublicId(publicId)
                .orElseThrow(() -> error("QUESTION_NOT_FOUND", "La pregunta solicitada no existe."));
    }

    private QuestionJpaEntity locked(String id) {
        String publicId = PublicIdNormalizer.requiredUuid(id, "QUESTION_ID_INVALID",
                "La pregunta indicada no es válida.");
        return questions.findByPublicIdForUpdate(publicId)
                .orElseThrow(() -> error("QUESTION_NOT_FOUND", "La pregunta solicitada no existe."));
    }

    private void checkVersion(QuestionJpaEntity entity, long version) {
        if (entity.getVersion() != version) {
            throw error("QUESTION_CONCURRENT_MODIFICATION",
                    "La pregunta fue modificada por otra persona. Recarga la información.");
        }
    }

    private QuestionMediaJpaEntity optionalMedia(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        String publicId = PublicIdNormalizer.requiredUuid(id, "QUESTION_MEDIA_INVALID",
                "La imagen seleccionada no es válida.");
        return media.findByPublicId(publicId)
                .orElseThrow(() -> error("QUESTION_MEDIA_NOT_FOUND", "La imagen seleccionada no existe."));
    }

    private String writeAnswers(List<String> answers) {
        try {
            return answers == null || answers.isEmpty() ? null : json.writeValueAsString(answers);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private List<String> readAnswers(String answers) {
        try {
            return answers == null ? List.of() : json.readValue(answers, new TypeReference<List<String>>() { });
        } catch (Exception exception) {
            return List.of();
        }
    }

    private boolean effectiveManual(String type, boolean requested) {
        try {
            return QuestionTypeCode.valueOf(type).requiresManualReview() || requested;
        } catch (Exception exception) {
            return requested;
        }
    }

    private QuestionDetail toDetail(QuestionJpaEntity entity) {
        return new QuestionDetail(entity.getPublicId(), entity.getStatement(), entity.getExplanation(),
                entity.getType().getCode(), entity.getType().getName(), entity.getDifficulty().getCode(),
                entity.getDifficulty().getName(), entity.getCategories().stream().map(this::catRef)
                        .sorted(Comparator.comparing(QuestionCategoryRef::name)).toList(),
                entity.getStatus(), entity.getVersion(), mediaView(entity.getPromptMedia()), entity.getCodeLanguage(),
                entity.getCodeContent(), new QuestionAnswerSettings(readAnswers(entity.getAcceptedAnswersJson()),
                        entity.isCaseSensitive(), entity.isManualReview(), entity.getNumericMin(),
                        entity.getNumericMax(), entity.getNumericTolerance(), entity.getResponseMaxLength()),
                entity.getOptions().stream().map(option -> new QuestionOptionView(option.getPublicId(),
                        option.getOptionOrder(), option.getText(), mediaView(option.getMedia()), option.isCorrect()))
                        .toList(),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private QuestionSummary toSummary(QuestionJpaEntity entity) {
        return new QuestionSummary(entity.getPublicId(), entity.getStatement(), entity.getType().getCode(),
                entity.getType().getName(), entity.getDifficulty().getCode(), entity.getDifficulty().getName(),
                entity.getCategories().stream().map(this::catRef)
                        .sorted(Comparator.comparing(QuestionCategoryRef::name)).toList(),
                entity.getStatus(), entity.getPromptMedia() != null
                        || entity.getOptions().stream().anyMatch(option -> option.getMedia() != null),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }

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

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private String nullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String code(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private String truncate(String value, int maxLength) {
        String normalized = nullable(value);
        if (normalized == null || normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength);
    }

    private BusinessException error(String code, String message) {
        return new BusinessException(code, message);
    }

    private record Selection(QuestionTypeJpaEntity type, QuestionDifficultyJpaEntity difficulty,
            Set<QuestionCategoryJpaEntity> categories) {
    }
}
