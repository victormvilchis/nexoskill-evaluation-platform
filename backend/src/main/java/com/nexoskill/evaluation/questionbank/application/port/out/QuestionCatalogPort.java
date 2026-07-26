package com.nexoskill.evaluation.questionbank.application.port.out;

import com.nexoskill.evaluation.organizations.domain.model.TenantContext;
import com.nexoskill.evaluation.questionbank.application.model.*;
import com.nexoskill.evaluation.questionbank.domain.model.CatalogStatus;
import java.util.List;

public interface QuestionCatalogPort {
    QuestionCatalogs activeCatalogs(TenantContext tenant);

    List<QuestionCategorySummary> categories(TenantContext tenant, CatalogStatus status);

    QuestionCategorySummary get(String publicId, TenantContext tenant);

    QuestionCategorySummary create(CategoryCommands.Create command);

    QuestionCategorySummary update(CategoryCommands.Update command);

    QuestionCategorySummary changeStatus(CategoryCommands.ChangeStatus command);

    QuestionCategorySummary softDelete(CategoryCommands.Delete command);

    QuestionCategoryDependencies dependencies(String publicId, TenantContext tenant);

    List<QuestionCategoryStatusHistory> history(String publicId, TenantContext tenant);
}
