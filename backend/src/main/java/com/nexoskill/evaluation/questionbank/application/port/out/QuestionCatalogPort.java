package com.nexoskill.evaluation.questionbank.application.port.out;

import com.nexoskill.evaluation.questionbank.application.model.CatalogOption;
import com.nexoskill.evaluation.questionbank.application.model.QuestionCategorySummary;
import com.nexoskill.evaluation.questionbank.domain.model.CatalogStatus;
import java.util.List;

public interface QuestionCatalogPort {

    List<CatalogOption> listActiveTypes();

    List<CatalogOption> listActiveDifficulties();

    List<QuestionCategorySummary> listActiveCategories();

    List<QuestionCategorySummary> listCategories();

    QuestionCategorySummary createCategory(NewCategoryData category);

    boolean categoryCodeExists(String code);

    boolean categoryNameExists(String normalizedName);

    CategoryStatusChange changeCategoryStatus(String publicId, CatalogStatus targetStatus);

    record CategoryStatusChange(
            QuestionCategorySummary category,
            CatalogStatus previousStatus
    ) {
    }

    record NewCategoryData(
            String publicId,
            String code,
            String name,
            String description,
            Long createdBy
    ) {
    }
}
