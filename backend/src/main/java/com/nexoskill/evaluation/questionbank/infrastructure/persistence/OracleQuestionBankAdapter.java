package com.nexoskill.evaluation.questionbank.infrastructure.persistence;

import com.nexoskill.evaluation.questionbank.application.model.QuestionDetail;
import com.nexoskill.evaluation.questionbank.application.model.QuestionOptionCommand;
import com.nexoskill.evaluation.questionbank.application.model.QuestionOptionView;
import com.nexoskill.evaluation.questionbank.application.model.QuestionPage;
import com.nexoskill.evaluation.questionbank.application.model.QuestionSummary;
import com.nexoskill.evaluation.questionbank.application.model.QuestionVersionSummary;
import com.nexoskill.evaluation.questionbank.application.port.out.QuestionBankPort;
import com.nexoskill.evaluation.questionbank.domain.model.CatalogStatus;
import com.nexoskill.evaluation.questionbank.domain.model.QuestionStatus;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
public class OracleQuestionBankAdapter implements QuestionBankPort {

    private final SpringDataQuestionRepository questionRepository;
    private final SpringDataQuestionVersionRepository versionRepository;
    private final SpringDataQuestionTypeRepository typeRepository;
    private final SpringDataQuestionDifficultyRepository difficultyRepository;
    private final SpringDataQuestionCategoryRepository categoryRepository;
    private final Clock clock;

    public OracleQuestionBankAdapter(
            SpringDataQuestionRepository questionRepository,
            SpringDataQuestionVersionRepository versionRepository,
            SpringDataQuestionTypeRepository typeRepository,
            SpringDataQuestionDifficultyRepository difficultyRepository,
            SpringDataQuestionCategoryRepository categoryRepository,
            Clock clock) {
        this.questionRepository = questionRepository;
        this.versionRepository = versionRepository;
        this.typeRepository = typeRepository;
        this.difficultyRepository = difficultyRepository;
        this.categoryRepository = categoryRepository;
        this.clock = clock;
    }

    @Override
    public QuestionDetail create(NewQuestionData data) {
        CatalogSelection catalogs = resolveCatalogs(
                data.typeCode(), data.difficultyCode(), data.categoryPublicId());
        Instant now = clock.instant();
        QuestionJpaEntity question = QuestionJpaEntity.create(
                data.publicId(), catalogs.type(), catalogs.difficulty(),
                catalogs.category(), data.createdBy(), now);
        QuestionJpaEntity persistedQuestion = questionRepository.save(question);
        QuestionVersionJpaEntity version = createVersion(
                persistedQuestion, 1, data.statement(), data.explanation(),
                null, data.options(), data.createdBy(), now);
        QuestionVersionJpaEntity persistedVersion = versionRepository.save(version);
        persistedQuestion.registerCurrentVersion(persistedVersion);
        return toDetail(questionRepository.saveAndFlush(persistedQuestion));
    }

    @Override
    public UpdateResult update(UpdateQuestionData data) {
        QuestionJpaEntity question = requiredQuestion(data.publicId());
        assertExpectedVersion(question, data.expectedEntityVersion());
        if (question.getStatus() != QuestionStatus.DRAFT
                && question.getStatus() != QuestionStatus.PUBLISHED) {
            throw new BusinessException(
                    "QUESTION_NOT_EDITABLE",
                    "Solo pueden editarse preguntas en borrador o publicadas."
            );
        }

        CatalogSelection catalogs = resolveCatalogsForUpdate(
                question, data.typeCode(), data.difficultyCode(), data.categoryPublicId());
        Instant now = clock.instant();
        int previousVersion = requiredCurrentVersion(question).getVersionNumber();
        question.updateClassification(
                catalogs.type(), catalogs.difficulty(), catalogs.category(),
                data.updatedBy(), now);

        boolean createNewVersion = question.getStatus() == QuestionStatus.PUBLISHED;
        if (createNewVersion && (data.changeSummary() == null || data.changeSummary().isBlank())) {
            throw new BusinessException(
                    "QUESTION_CHANGE_SUMMARY_REQUIRED",
                    "Describe el cambio realizado para crear una nueva versión."
            );
        }
        if (createNewVersion) {
            int nextVersion = versionRepository
                    .findByQuestion_IdOrderByVersionNumberDesc(question.getId())
                    .stream()
                    .mapToInt(QuestionVersionJpaEntity::getVersionNumber)
                    .max()
                    .orElse(0) + 1;
            QuestionVersionJpaEntity newVersion = createVersion(
                    question, nextVersion, data.statement(), data.explanation(),
                    data.changeSummary(), data.options(), data.updatedBy(), now);
            QuestionVersionJpaEntity persisted = versionRepository.save(newVersion);
            question.registerNewDraftVersion(persisted, data.updatedBy(), now);
        } else {
            QuestionVersionJpaEntity current = requiredCurrentVersion(question);
            current.updateDraft(
                    data.statement(), data.explanation(), data.changeSummary(),
                    buildOptions(current, data.options(), now));
            versionRepository.save(current);
        }
        return new UpdateResult(
                toDetail(questionRepository.saveAndFlush(question)),
                previousVersion,
                createNewVersion
        );
    }

    @Override
    public TransitionResult transition(
            String publicId,
            QuestionStatus targetStatus,
            long expectedEntityVersion,
            Long actorUserId) {
        QuestionJpaEntity question = requiredQuestion(publicId);
        assertExpectedVersion(question, expectedEntityVersion);
        QuestionStatus previous = question.getStatus();
        Instant now = clock.instant();
        QuestionVersionJpaEntity current = requiredCurrentVersion(question);
        QuestionVersionJpaEntity previouslyPublished = question.getPublishedVersion();
        if (targetStatus == QuestionStatus.PUBLISHED
                && previouslyPublished != null
                && !previouslyPublished.getId().equals(current.getId())) {
            previouslyPublished.transitionTo(QuestionStatus.ARCHIVED, actorUserId, now);
            versionRepository.save(previouslyPublished);
        }
        question.transitionTo(targetStatus, actorUserId, now);
        current.transitionTo(targetStatus, actorUserId, now);
        versionRepository.save(current);
        QuestionDetail detail = toDetail(questionRepository.saveAndFlush(question));
        return new TransitionResult(detail, previous, targetStatus);
    }

    @Override
    public QuestionDetail duplicate(
            String sourcePublicId,
            String newPublicId,
            Long actorUserId) {
        QuestionJpaEntity source = requiredQuestion(sourcePublicId);
        QuestionVersionJpaEntity sourceVersion = requiredCurrentVersion(source);
        Instant now = clock.instant();
        QuestionJpaEntity copy = QuestionJpaEntity.create(
                newPublicId, source.getType(), source.getDifficulty(),
                source.getCategory(), actorUserId, now);
        QuestionJpaEntity persisted = questionRepository.save(copy);
        List<QuestionOptionCommand> options = sourceVersion.getOptions().stream()
                .map(option -> new QuestionOptionCommand(option.getText(), option.isCorrect()))
                .toList();
        QuestionVersionJpaEntity version = createVersion(
                persisted, 1,
                sourceVersion.getStatement() + " (copia)",
                sourceVersion.getExplanation(),
                "Duplicada desde " + sourcePublicId,
                options, actorUserId, now);
        QuestionVersionJpaEntity persistedVersion = versionRepository.save(version);
        persisted.registerCurrentVersion(persistedVersion);
        return toDetail(questionRepository.saveAndFlush(persisted));
    }

    @Override
    public QuestionPage search(
            String query,
            QuestionStatus status,
            String typeCode,
            String difficultyCode,
            String categoryPublicId,
            int page,
            int size) {
        var result = questionRepository.search(
                query,
                status == null ? null : status.name(),
                typeCode,
                difficultyCode,
                categoryPublicId,
                PageRequest.of(page, size)
        );
        return new QuestionPage(
                result.getContent().stream().map(this::toSummary).toList(),
                result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages()
        );
    }

    @Override
    public QuestionDetail getByPublicId(String publicId) {
        return toDetail(requiredQuestion(publicId));
    }

    @Override
    public List<QuestionVersionSummary> getVersions(String publicId) {
        QuestionJpaEntity question = requiredQuestion(publicId);
        return versionRepository.findByQuestion_IdOrderByVersionNumberDesc(question.getId())
                .stream()
                .map(version -> new QuestionVersionSummary(
                        version.getVersionNumber(), version.getStatus(),
                        version.getStatement(), version.getChangeSummary(),
                        version.getCreatedAt(), version.getStatusChangedAt(),
                        version.getPublishedAt()
                ))
                .toList();
    }

    private QuestionVersionJpaEntity createVersion(
            QuestionJpaEntity question,
            int versionNumber,
            String statement,
            String explanation,
            String changeSummary,
            List<QuestionOptionCommand> options,
            Long actorUserId,
            Instant now) {
        QuestionVersionJpaEntity version = QuestionVersionJpaEntity.create(
                question, versionNumber, statement, explanation,
                changeSummary, actorUserId, now);
        int order = 1;
        for (QuestionOptionCommand option : options) {
            version.addOption(QuestionOptionJpaEntity.create(
                    version, UUID.randomUUID().toString(), order++,
                    option.text(), option.correct(), now));
        }
        return version;
    }

    private List<QuestionOptionJpaEntity> buildOptions(
            QuestionVersionJpaEntity version,
            List<QuestionOptionCommand> options,
            Instant now) {
        List<QuestionOptionJpaEntity> result = new ArrayList<>();
        int order = 1;
        for (QuestionOptionCommand option : options) {
            result.add(QuestionOptionJpaEntity.create(
                    version, UUID.randomUUID().toString(), order++,
                    option.text(), option.correct(), now));
        }
        return result;
    }

    private CatalogSelection resolveCatalogsForUpdate(
            QuestionJpaEntity question,
            String typeCode,
            String difficultyCode,
            String categoryPublicId) {
        QuestionTypeJpaEntity type = typeRepository.findById(typeCode)
                .filter(value -> value.getStatus() == CatalogStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(
                        "QUESTION_TYPE_NOT_FOUND",
                        "El tipo de pregunta no está disponible."));
        QuestionDifficultyJpaEntity difficulty = difficultyRepository
                .findById(difficultyCode)
                .filter(value -> value.getStatus() == CatalogStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(
                        "QUESTION_DIFFICULTY_NOT_FOUND",
                        "La dificultad no está disponible."));
        QuestionCategoryJpaEntity category = categoryRepository.findByPublicId(categoryPublicId)
                .orElseThrow(() -> new BusinessException(
                        "QUESTION_CATEGORY_NOT_FOUND",
                        "La categoría seleccionada no existe."));
        boolean sameCategory = question.getCategory().getPublicId()
                .equals(category.getPublicId());
        if (category.getStatus() != CatalogStatus.ACTIVE && !sameCategory) {
            throw new BusinessException(
                    "QUESTION_CATEGORY_NOT_FOUND",
                    "La categoría seleccionada no está disponible."
            );
        }
        return new CatalogSelection(type, difficulty, category);
    }

    private CatalogSelection resolveCatalogs(
            String typeCode,
            String difficultyCode,
            String categoryPublicId) {
        QuestionTypeJpaEntity type = typeRepository.findById(typeCode)
                .filter(value -> value.getStatus() == CatalogStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(
                        "QUESTION_TYPE_NOT_FOUND",
                        "El tipo de pregunta no está disponible."));
        QuestionDifficultyJpaEntity difficulty = difficultyRepository
                .findById(difficultyCode)
                .filter(value -> value.getStatus() == CatalogStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(
                        "QUESTION_DIFFICULTY_NOT_FOUND",
                        "La dificultad no está disponible."));
        QuestionCategoryJpaEntity category = categoryRepository
                .findByPublicIdAndStatus(categoryPublicId, CatalogStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(
                        "QUESTION_CATEGORY_NOT_FOUND",
                        "La categoría seleccionada no está disponible."));
        return new CatalogSelection(type, difficulty, category);
    }

    private QuestionJpaEntity requiredQuestion(String publicId) {
        return questionRepository.findByPublicId(publicId)
                .orElseThrow(() -> new BusinessException(
                        "QUESTION_NOT_FOUND",
                        "La pregunta solicitada no existe."));
    }

    private void assertExpectedVersion(QuestionJpaEntity question, long expected) {
        if (question.getVersion() != expected) {
            throw new BusinessException(
                    "QUESTION_CONCURRENT_MODIFICATION",
                    "La pregunta fue modificada por otra sesión. Actualiza la página e intenta nuevamente."
            );
        }
    }

    private QuestionSummary toSummary(QuestionJpaEntity entity) {
        QuestionVersionJpaEntity version = requiredCurrentVersion(entity);
        return new QuestionSummary(
                entity.getPublicId(), version.getStatement(),
                entity.getType().getCode(), entity.getType().getName(),
                entity.getDifficulty().getCode(), entity.getDifficulty().getName(),
                entity.getCategory().getPublicId(), entity.getCategory().getName(),
                entity.getStatus(), version.getVersionNumber(),
                entity.getCreatedAt(), entity.getUpdatedAt()
        );
    }

    private QuestionDetail toDetail(QuestionJpaEntity entity) {
        QuestionVersionJpaEntity version = requiredCurrentVersion(entity);
        List<QuestionOptionView> options = version.getOptions().stream()
                .map(option -> new QuestionOptionView(
                        option.getPublicId(), option.getOptionOrder(),
                        option.getText(), option.isCorrect()))
                .toList();
        Integer publishedVersion = entity.getPublishedVersion() == null
                ? null : entity.getPublishedVersion().getVersionNumber();
        return new QuestionDetail(
                entity.getPublicId(), version.getStatement(), version.getExplanation(),
                entity.getType().getCode(), entity.getType().getName(),
                entity.getDifficulty().getCode(), entity.getDifficulty().getName(),
                entity.getCategory().getPublicId(), entity.getCategory().getName(),
                entity.getStatus(), version.getVersionNumber(), entity.getVersion(),
                publishedVersion, options, entity.getCreatedAt(), entity.getUpdatedAt()
        );
    }

    private QuestionVersionJpaEntity requiredCurrentVersion(QuestionJpaEntity entity) {
        if (entity.getCurrentVersion() == null) {
            throw new BusinessException(
                    "QUESTION_VERSION_MISSING",
                    "La pregunta no tiene una versión vigente."
            );
        }
        return entity.getCurrentVersion();
    }

    private record CatalogSelection(
            QuestionTypeJpaEntity type,
            QuestionDifficultyJpaEntity difficulty,
            QuestionCategoryJpaEntity category) {
    }
}
