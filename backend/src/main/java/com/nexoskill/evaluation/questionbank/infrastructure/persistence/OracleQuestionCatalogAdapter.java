package com.nexoskill.evaluation.questionbank.infrastructure.persistence;

import com.nexoskill.evaluation.questionbank.application.model.CatalogOption;
import com.nexoskill.evaluation.questionbank.application.model.QuestionCategorySummary;
import com.nexoskill.evaluation.questionbank.application.port.out.QuestionCatalogPort;
import com.nexoskill.evaluation.questionbank.domain.model.CatalogStatus;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class OracleQuestionCatalogAdapter implements QuestionCatalogPort {

    private final SpringDataQuestionTypeRepository typeRepository;
    private final SpringDataQuestionDifficultyRepository difficultyRepository;
    private final SpringDataQuestionCategoryRepository categoryRepository;
    private final Clock clock;

    public OracleQuestionCatalogAdapter(
            SpringDataQuestionTypeRepository typeRepository,
            SpringDataQuestionDifficultyRepository difficultyRepository,
            SpringDataQuestionCategoryRepository categoryRepository,
            Clock clock) {
        this.typeRepository = typeRepository;
        this.difficultyRepository = difficultyRepository;
        this.categoryRepository = categoryRepository;
        this.clock = clock;
    }

    @Override
    public List<CatalogOption> listActiveTypes() {
        return typeRepository.findAllByStatusOrderByNameAsc(CatalogStatus.ACTIVE)
                .stream()
                .map(type -> new CatalogOption(
                        type.getCode(),
                        type.getName(),
                        type.getDescription()
                ))
                .toList();
    }

    @Override
    public List<CatalogOption> listActiveDifficulties() {
        return difficultyRepository
                .findAllByStatusOrderBySortOrderAsc(CatalogStatus.ACTIVE)
                .stream()
                .map(difficulty -> new CatalogOption(
                        difficulty.getCode(),
                        difficulty.getName(),
                        null
                ))
                .toList();
    }

    @Override
    public List<QuestionCategorySummary> listActiveCategories() {
        return categoryRepository.findAllByStatusOrderByNameAsc(CatalogStatus.ACTIVE)
                .stream()
                .map(this::toSummary)
                .toList();
    }

    @Override
    public List<QuestionCategorySummary> listCategories() {
        return categoryRepository.findAllByOrderByNameAsc()
                .stream()
                .map(this::toSummary)
                .toList();
    }

    @Override
    public QuestionCategorySummary createCategory(NewCategoryData category) {
        return toSummary(categoryRepository.save(
                QuestionCategoryJpaEntity.create(
                        category.publicId(),
                        category.code(),
                        category.name(),
                        category.description(),
                        category.createdBy(),
                        clock.instant()
                )
        ));
    }

    @Override
    public boolean categoryCodeExists(String code) {
        return categoryRepository.existsByCodeIgnoreCase(code);
    }

    @Override
    public boolean categoryNameExists(String normalizedName) {
        return categoryRepository.existsByNormalizedName(normalizedName);
    }

    @Override
    public CategoryStatusChange changeCategoryStatus(
            String publicId,
            CatalogStatus targetStatus) {
        QuestionCategoryJpaEntity category = categoryRepository.findByPublicId(publicId)
                .orElseThrow(() -> new com.nexoskill.evaluation.shared.domain.BusinessException(
                        "QUESTION_CATEGORY_NOT_FOUND",
                        "La categoría solicitada no existe."
                ));
        CatalogStatus previous = category.getStatus();
        category.changeStatus(targetStatus);
        return new CategoryStatusChange(
                toSummary(categoryRepository.saveAndFlush(category)),
                previous
        );
    }

    private QuestionCategorySummary toSummary(QuestionCategoryJpaEntity entity) {
        return new QuestionCategorySummary(
                entity.getPublicId(),
                entity.getCode(),
                entity.getName(),
                entity.getDescription(),
                entity.getStatus(),
                entity.getCreatedAt()
        );
    }
}
