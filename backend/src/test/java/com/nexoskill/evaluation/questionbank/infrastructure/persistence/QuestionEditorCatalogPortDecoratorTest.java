package com.nexoskill.evaluation.questionbank.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nexoskill.evaluation.organizations.domain.model.TenantContext;
import com.nexoskill.evaluation.questionbank.domain.model.CatalogStatus;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QuestionEditorCatalogPortDecoratorTest {

    @Mock
    private OracleQuestionCatalogAdapter delegate;

    @Mock
    private SpringDataQuestionTypeRepository types;

    @Mock
    private SpringDataQuestionDifficultyRepository difficulties;

    @Mock
    private QuestionTypeJpaEntity type;

    @Mock
    private QuestionDifficultyJpaEntity difficulty;

    private QuestionEditorCatalogPortDecorator adapter;

    @BeforeEach
    void setUp() {
        adapter = new QuestionEditorCatalogPortDecorator(delegate, types, difficulties);
    }

    @Test
    void loadsEditorCatalogsWithoutResolvingTenantCategoriesOrTechnologies() {
        when(type.getCode()).thenReturn("SINGLE_CHOICE");
        when(type.getName()).thenReturn("Opción única");
        when(type.getDescription()).thenReturn("Una sola respuesta correcta.");
        when(difficulty.getCode()).thenReturn("JR");
        when(difficulty.getName()).thenReturn("JR");
        when(difficulty.getDescription()).thenReturn("Nivel JR");
        when(types.findAllByStatusOrderByNameAsc(CatalogStatus.ACTIVE)).thenReturn(List.of(type));
        when(difficulties.findAllByStatusOrderBySortOrderAsc(CatalogStatus.ACTIVE))
                .thenReturn(List.of(difficulty));

        var result = adapter.activeCatalogs(TenantContext.global(1L, "global", "GLOBAL"));

        assertEquals(List.of("SINGLE_CHOICE"), result.types().stream().map(value -> value.code()).toList());
        assertEquals(List.of("JR"), result.difficulties().stream().map(value -> value.code()).toList());
        assertTrue(result.technologies().isEmpty());
        assertTrue(result.categories().isEmpty());
        verifyNoInteractions(delegate);
    }

    @Test
    void keepsTechnologyOptionsOnTheOwnerAwareDelegate() {
        TenantContext tenant = TenantContext.organization(20L, "organization", "ORG", false);

        adapter.technologyOptions(tenant, null, "ORGANIZATION", "organization");

        verify(delegate).technologyOptions(tenant, null, "ORGANIZATION", "organization");
        verifyNoInteractions(types, difficulties);
    }

    @Test
    void keepsCategoryOptionsOnTheOwnerAwareDelegate() {
        TenantContext tenant = TenantContext.organization(20L, "organization", "ORG", false);

        adapter.questionOptions(tenant, null, "ORGANIZATION", "organization");

        verify(delegate).questionOptions(tenant, null, "ORGANIZATION", "organization");
        verifyNoInteractions(types, difficulties);
    }
}
