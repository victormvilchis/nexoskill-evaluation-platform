package com.nexoskill.evaluation.questionbank.infrastructure.persistence;

import com.nexoskill.evaluation.questionbank.application.model.QuestionDetail;
import com.nexoskill.evaluation.questionbank.application.model.QuestionOptionCommand;
import com.nexoskill.evaluation.questionbank.application.model.QuestionOptionView;
import com.nexoskill.evaluation.questionbank.application.model.QuestionPage;
import com.nexoskill.evaluation.questionbank.application.model.QuestionSummary;
import com.nexoskill.evaluation.questionbank.application.port.out.QuestionBankPort;
import com.nexoskill.evaluation.questionbank.domain.model.CatalogStatus;
import com.nexoskill.evaluation.questionbank.domain.model.QuestionStatus;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import java.time.Clock;
import java.time.Instant;
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
        QuestionTypeJpaEntity type = typeRepository.findById(data.typeCode())
                .filter(value -> value.getStatus() == CatalogStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(
                        "QUESTION_TYPE_NOT_FOUND",
                        "El tipo de pregunta no está disponible."
                ));
        QuestionDifficultyJpaEntity difficulty = difficultyRepository
                .findById(data.difficultyCode())
                .filter(value -> value.getStatus() == CatalogStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(
                        "QUESTION_DIFFICULTY_NOT_FOUND",
                        "La dificultad no está disponible."
                ));
        QuestionCategoryJpaEntity category = categoryRepository
                .findByPublicIdAndStatus(data.categoryPublicId(), CatalogStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(
                        "QUESTION_CATEGORY_NOT_FOUND",
                        "La categoría seleccionada no está disponible."
                ));

        Instant now = clock.instant();
        QuestionJpaEntity question = QuestionJpaEntity.create(
                data.publicId(),
                type,
                difficulty,
                category,
                data.createdBy(),
                now
        );
        QuestionJpaEntity persistedQuestion = questionRepository.save(question);
        QuestionVersionJpaEntity version = QuestionVersionJpaEntity.create(
                persistedQuestion,
                1,
                data.statement(),
                data.explanation(),
                data.createdBy(),
                now
        );

        int order = 1;
        for (QuestionOptionCommand option : data.options()) {
            version.addOption(QuestionOptionJpaEntity.create(
                    version,
                    UUID.randomUUID().toString(),
                    order++,
                    option.text(),
                    option.correct(),
                    now
            ));
        }
        QuestionVersionJpaEntity persistedVersion = versionRepository.save(version);
        persistedQuestion.registerCurrentVersion(persistedVersion);
        return toDetail(questionRepository.save(persistedQuestion));
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
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    @Override
    public QuestionDetail getByPublicId(String publicId) {
        return questionRepository.findByPublicId(publicId)
                .map(this::toDetail)
                .orElseThrow(() -> new BusinessException(
                        "QUESTION_NOT_FOUND",
                        "La pregunta solicitada no existe."
                ));
    }

    private QuestionSummary toSummary(QuestionJpaEntity entity) {
        QuestionVersionJpaEntity version = requiredCurrentVersion(entity);
        return new QuestionSummary(
                entity.getPublicId(),
                version.getStatement(),
                entity.getType().getCode(),
                entity.getType().getName(),
                entity.getDifficulty().getCode(),
                entity.getDifficulty().getName(),
                entity.getCategory().getPublicId(),
                entity.getCategory().getName(),
                entity.getStatus(),
                version.getVersionNumber(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private QuestionDetail toDetail(QuestionJpaEntity entity) {
        QuestionVersionJpaEntity version = requiredCurrentVersion(entity);
        List<QuestionOptionView> options = version.getOptions().stream()
                .map(option -> new QuestionOptionView(
                        option.getPublicId(),
                        option.getOptionOrder(),
                        option.getText(),
                        option.isCorrect()
                ))
                .toList();
        return new QuestionDetail(
                entity.getPublicId(),
                version.getStatement(),
                version.getExplanation(),
                entity.getType().getCode(),
                entity.getType().getName(),
                entity.getDifficulty().getCode(),
                entity.getDifficulty().getName(),
                entity.getCategory().getPublicId(),
                entity.getCategory().getName(),
                entity.getStatus(),
                version.getVersionNumber(),
                options,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
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
}
