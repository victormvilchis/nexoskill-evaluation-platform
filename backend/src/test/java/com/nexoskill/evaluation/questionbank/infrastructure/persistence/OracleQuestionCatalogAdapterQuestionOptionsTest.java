package com.nexoskill.evaluation.questionbank.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.nexoskill.evaluation.globalcontent.application.service.ContentSynchronizationService;
import com.nexoskill.evaluation.globalcontent.application.service.GlobalContentAccessPolicy;
import com.nexoskill.evaluation.organizations.domain.model.ContentScope;
import com.nexoskill.evaluation.organizations.domain.model.TenantContext;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationRepository;
import com.nexoskill.evaluation.questionbank.domain.model.CatalogStatus;
import com.nexoskill.evaluation.questionbank.application.service.QuestionCreationTargetResolver;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OracleQuestionCatalogAdapterQuestionOptionsTest {
    private static final String QUESTION_ID = "00000000-0000-0000-0000-000000000001";

    @Mock private SpringDataQuestionTypeRepository types;
    @Mock private SpringDataQuestionDifficultyRepository difficulties;
    @Mock private SpringDataQuestionTechnologyRepository technologies;
    @Mock private SpringDataQuestionCategoryRepository categories;
    @Mock private SpringDataQuestionRepository questions;
    @Mock private QuestionCategoryStatusHistoryRepository history;
    @Mock private OrganizationRepository organizations;
    @Mock private GlobalContentAccessPolicy accessPolicy;
    @Mock private QuestionCreationTargetResolver creationTargetResolver;
    @Mock private ContentSynchronizationService synchronization;
    @Mock private QuestionCategoryJpaEntity category;
    @Mock private QuestionJpaEntity question;

    private OracleQuestionCatalogAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new OracleQuestionCatalogAdapter(types, difficulties, technologies, categories, questions,
                history, organizations, accessPolicy, creationTargetResolver, synchronization,
                Clock.fixed(Instant.parse("2026-07-29T12:00:00Z"), ZoneOffset.UTC));
        lenient().when(category.getId()).thenReturn(10L);
        lenient().when(category.getPublicId()).thenReturn("00000000-0000-0000-0000-000000000010");
        lenient().when(category.getCode()).thenReturn("JAVA");
        lenient().when(category.getName()).thenReturn("Java");
        lenient().when(category.getStatus()).thenReturn(CatalogStatus.ACTIVE);
        lenient().when(category.getContentScope()).thenReturn(ContentScope.GLOBAL);
        lenient().when(category.getOwnerOrganizationId()).thenReturn(1L);
        lenient().when(category.getVersion()).thenReturn(0L);
        lenient().when(category.getCreatedAt()).thenReturn(Instant.parse("2026-07-29T12:00:00Z"));
        lenient().when(organizations.findById(1L)).thenReturn(Optional.empty());
        lenient().when(questions.countAllByCategory(10L)).thenReturn(0L);
    }

    @Test
    void loadsAllActiveCategoriesForTheCreationOwner() {
        TenantContext tenant = TenantContext.global(1L, "global", "GLOBAL");
        when(creationTargetResolver.resolve(tenant, null, null))
                .thenReturn(new QuestionCreationTargetResolver.Target(ContentScope.GLOBAL, 1L, "global"));
        when(categories.findAllByContentScopeOrderByNameAsc(ContentScope.GLOBAL)).thenReturn(List.of(category));

        var result = adapter.questionOptions(tenant, null, null, null);

        assertEquals(List.of("Java"), result.stream().map(value -> value.name()).toList());
    }

    @Test
    void retainsAnInactiveCategoryAlreadyAssignedDuringEdit() {
        TenantContext tenant = TenantContext.organization(2L, "customer", "CUSTOMER", false);
        when(question.getId()).thenReturn(20L);
        when(question.getContentScope()).thenReturn(ContentScope.ORGANIZATION);
        when(question.getOwnerOrganizationId()).thenReturn(2L);
        when(question.getCategories()).thenReturn(Set.of(category));
        when(questions.findByPublicId(QUESTION_ID)).thenReturn(Optional.of(question));
        lenient().when(category.getStatus()).thenReturn(CatalogStatus.INACTIVE);
        lenient().when(category.getContentScope()).thenReturn(ContentScope.ORGANIZATION);
        lenient().when(category.getOwnerOrganizationId()).thenReturn(2L);
        when(categories.findAllByContentScopeAndOwnerOrganizationIdOrderByNameAsc(
                ContentScope.ORGANIZATION, 2L)).thenReturn(List.of(category));
        when(organizations.findById(2L)).thenReturn(Optional.empty());

        var result = adapter.questionOptions(tenant, QUESTION_ID, null, null);

        assertEquals(1, result.size());
        assertEquals(CatalogStatus.INACTIVE, result.getFirst().status());
    }
    @Test
    void supportsLegacyGlobalCategoriesWithoutOwner() {
        TenantContext tenant = TenantContext.global(1L, "global", "GLOBAL");
        when(creationTargetResolver.resolve(tenant, null, null))
                .thenReturn(new QuestionCreationTargetResolver.Target(ContentScope.GLOBAL, 1L, "global"));
        lenient().when(category.getOwnerOrganizationId()).thenReturn(null);
        when(categories.findAllByContentScopeOrderByNameAsc(ContentScope.GLOBAL)).thenReturn(List.of(category));

        var result = adapter.questionOptions(tenant, null, null, null);

        assertEquals(1, result.size());
        assertEquals("Java", result.getFirst().name());
    }

    @Test
    void excludesCategoriesOwnedByAnotherOrganization() {
        TenantContext tenant = TenantContext.organization(2L, "customer", "CUSTOMER", false);
        when(creationTargetResolver.resolve(tenant, null, null))
                .thenReturn(new QuestionCreationTargetResolver.Target(ContentScope.ORGANIZATION, 2L, "customer"));
        lenient().when(category.getContentScope()).thenReturn(ContentScope.ORGANIZATION);
        lenient().when(category.getOwnerOrganizationId()).thenReturn(3L);
        when(categories.findAllByContentScopeAndOwnerOrganizationIdOrderByNameAsc(
                ContentScope.ORGANIZATION, 2L)).thenReturn(List.of());

        var result = adapter.questionOptions(tenant, null, null, null);

        assertEquals(0, result.size());
    }

}
