package com.nexoskill.evaluation.questionbank.infrastructure.persistence;

import com.nexoskill.evaluation.organizations.domain.model.TenantContext;
import com.nexoskill.evaluation.questionbank.application.model.CatalogOption;
import com.nexoskill.evaluation.questionbank.application.model.CategoryCommands;
import com.nexoskill.evaluation.questionbank.application.model.QuestionCatalogs;
import com.nexoskill.evaluation.questionbank.application.model.QuestionCategoryDependencies;
import com.nexoskill.evaluation.questionbank.application.model.QuestionCategoryStatusHistory;
import com.nexoskill.evaluation.questionbank.application.model.QuestionCategorySummary;
import com.nexoskill.evaluation.questionbank.application.port.out.QuestionCatalogPort;
import com.nexoskill.evaluation.questionbank.domain.model.CatalogStatus;
import java.util.List;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Keeps the editor's immutable catalogs independent from tenant-owned categories
 * and optional technology catalogs. Categories are loaded through the dedicated
 * question-options query with the effective question owner.
 */
@Component
@Primary
public class QuestionEditorCatalogPortDecorator implements QuestionCatalogPort {

    private final OracleQuestionCatalogAdapter delegate;
    private final SpringDataQuestionTypeRepository types;
    private final SpringDataQuestionDifficultyRepository difficulties;

    public QuestionEditorCatalogPortDecorator(
            OracleQuestionCatalogAdapter delegate,
            SpringDataQuestionTypeRepository types,
            SpringDataQuestionDifficultyRepository difficulties) {
        this.delegate = delegate;
        this.types = types;
        this.difficulties = difficulties;
    }

    @Override
    public QuestionCatalogs activeCatalogs(TenantContext tenant) {
        return new QuestionCatalogs(
                types.findAllByStatusOrderByNameAsc(CatalogStatus.ACTIVE).stream()
                        .map(type -> new CatalogOption(type.getCode(), type.getName(), type.getDescription()))
                        .toList(),
                difficulties.findAllByStatusOrderBySortOrderAsc(CatalogStatus.ACTIVE).stream()
                        .map(value -> new CatalogOption(value.getCode(), value.getName(), value.getDescription()))
                        .toList(),
                List.of(),
                List.of());
    }

    @Override
    public List<QuestionCategorySummary> categories(TenantContext tenant, CatalogStatus status) {
        return delegate.categories(tenant, status);
    }

    @Override
    public List<QuestionCategorySummary> questionOptions(
            TenantContext tenant,
            String questionPublicId,
            String targetScope,
            String organizationPublicId) {
        return delegate.questionOptions(tenant, questionPublicId, targetScope, organizationPublicId);
    }

    @Override
    public QuestionCategorySummary get(String publicId, TenantContext tenant) {
        return delegate.get(publicId, tenant);
    }

    @Override
    public QuestionCategorySummary create(CategoryCommands.Create command) {
        return delegate.create(command);
    }

    @Override
    public QuestionCategorySummary update(CategoryCommands.Update command) {
        return delegate.update(command);
    }

    @Override
    public QuestionCategorySummary changeStatus(CategoryCommands.ChangeStatus command) {
        return delegate.changeStatus(command);
    }

    @Override
    public QuestionCategorySummary softDelete(CategoryCommands.Delete command) {
        return delegate.softDelete(command);
    }

    @Override
    public QuestionCategoryDependencies dependencies(String publicId, TenantContext tenant) {
        return delegate.dependencies(publicId, tenant);
    }

    @Override
    public List<QuestionCategoryStatusHistory> history(String publicId, TenantContext tenant) {
        return delegate.history(publicId, tenant);
    }
}
