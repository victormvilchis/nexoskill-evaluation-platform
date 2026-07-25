package com.nexoskill.evaluation.questionbank.application.port.out;

import com.nexoskill.evaluation.questionbank.application.model.CatalogOption;
import com.nexoskill.evaluation.questionbank.application.model.QuestionCategorySummary;
import java.util.List;

public interface QuestionCatalogPort {

    List<CatalogOption> listActiveTypes();

    List<CatalogOption> listActiveDifficulties();

    List<QuestionCategorySummary> listActiveCategories();

    QuestionCategorySummary createCategory(NewCategoryData category);

    boolean categoryCodeExists(String code);

    boolean categoryNameExists(String normalizedName);

    record NewCategoryData(
            String publicId,
            String code,
            String name,
            String description,
            Long createdBy
    ) {
    }
}
