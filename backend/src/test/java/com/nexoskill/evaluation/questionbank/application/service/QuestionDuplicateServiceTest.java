package com.nexoskill.evaluation.questionbank.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexoskill.evaluation.organizations.application.TenantContextResolver;
import com.nexoskill.evaluation.organizations.domain.model.ContentScope;
import com.nexoskill.evaluation.organizations.domain.model.TenantContext;
import com.nexoskill.evaluation.questionbank.application.model.CreateQuestionCommand;
import com.nexoskill.evaluation.questionbank.application.model.QuestionAnswerSettings;
import com.nexoskill.evaluation.questionbank.application.model.QuestionCategoryRef;
import com.nexoskill.evaluation.questionbank.application.model.QuestionDetail;
import com.nexoskill.evaluation.questionbank.application.model.QuestionOptionView;
import com.nexoskill.evaluation.questionbank.application.model.QuestionOwnershipView;
import com.nexoskill.evaluation.questionbank.application.model.QuestionTagView;
import com.nexoskill.evaluation.questionbank.application.port.out.QuestionBankPort;
import com.nexoskill.evaluation.questionbank.domain.model.CatalogStatus;
import com.nexoskill.evaluation.questionbank.domain.model.QuestionAvailabilityMode;
import com.nexoskill.evaluation.questionbank.domain.model.QuestionStatus;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class QuestionDuplicateServiceTest {
    private final QuestionBankPort port = mock(QuestionBankPort.class);
    private final QuestionSaveService save = mock(QuestionSaveService.class);
    private final TenantContextResolver tenantContextResolver = mock(TenantContextResolver.class);
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final QuestionServices.Duplicate service = new QuestionServices.Duplicate(
            port, save, tenantContextResolver, request);

    @Test
    void globalAdministratorDuplicatesGlobalQuestionAsUnpublishedGlobalContent() {
        TenantContext tenant = TenantContext.organization(20L, "org-20", "ORG_20", true);
        QuestionDetail source = question(ContentScope.GLOBAL, "global", "GLOBAL");
        QuestionDetail copy = question(ContentScope.GLOBAL, "global", "GLOBAL");
        when(port.get(source.publicId())).thenReturn(source);
        when(tenantContextResolver.resolve(request)).thenReturn(tenant);
        when(save.create(any(), eq(QuestionAvailabilityMode.NONE), eq(List.of()), eq(tenant), any()))
                .thenReturn(copy);

        assertThat(service.execute(source.publicId(), 7L)).isSameAs(copy);

        ArgumentCaptor<CreateQuestionCommand> command = ArgumentCaptor.forClass(CreateQuestionCommand.class);
        verify(save).create(command.capture(), eq(QuestionAvailabilityMode.NONE), eq(List.of()),
                eq(tenant), any(QuestionSaveService.Actor.class));
        assertThat(command.getValue().contentScope()).isEqualTo("GLOBAL");
        assertThat(command.getValue().organizationPublicId()).isNull();
        assertThat(command.getValue().categoryPublicIds()).containsExactly("category-1");
        assertThat(command.getValue().tags()).containsExactly("herencia");
        assertThat(command.getValue().options()).hasSize(2);
    }

    @Test
    void supervisorDuplicatesOnlyInsideOwnOrganization() {
        TenantContext tenant = TenantContext.organization(20L, "org-20", "ORG_20", false);
        QuestionDetail source = question(ContentScope.ORGANIZATION, "org-20", "ORG_20");
        when(port.get(source.publicId())).thenReturn(source);
        when(tenantContextResolver.resolve(request)).thenReturn(tenant);
        when(save.create(any(), isNull(), eq(List.of()), eq(tenant), any())).thenReturn(source);

        service.execute(source.publicId(), 7L);

        ArgumentCaptor<CreateQuestionCommand> command = ArgumentCaptor.forClass(CreateQuestionCommand.class);
        verify(save).create(command.capture(), isNull(), eq(List.of()), eq(tenant),
                any(QuestionSaveService.Actor.class));
        assertThat(command.getValue().contentScope()).isEqualTo("ORGANIZATION");
        assertThat(command.getValue().organizationPublicId()).isEqualTo("org-20");
    }

    @Test
    void supervisorCannotDuplicateGlobalQuestion() {
        TenantContext tenant = TenantContext.organization(20L, "org-20", "ORG_20", false);
        QuestionDetail source = question(ContentScope.GLOBAL, "global", "GLOBAL");
        when(port.get(source.publicId())).thenReturn(source);
        when(tenantContextResolver.resolve(request)).thenReturn(tenant);

        assertThatThrownBy(() -> service.execute(source.publicId(), 7L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("No tienes permisos para duplicar esta pregunta dentro del contexto actual.");

        verify(save, never()).create(any(), any(), any(), any(), any());
    }

    private static QuestionDetail question(ContentScope scope, String organizationPublicId,
            String organizationCode) {
        return new QuestionDetail(
                "11111111-1111-1111-1111-111111111111",
                "¿Cuál es la respuesta correcta?",
                "Explicación",
                "SINGLE_CHOICE",
                "Opción única",
                "JR",
                "JR",
                null,
                null,
                List.of(new QuestionCategoryRef("category-1", "JAVA", "Java", CatalogStatus.ACTIVE)),
                List.of(new QuestionTagView("tag-1", "herencia", "herencia")),
                QuestionStatus.ACTIVE,
                0L,
                null,
                null,
                null,
                QuestionAnswerSettings.empty(),
                List.of(
                        new QuestionOptionView("option-1", 1, "A", null, null, null, true, null),
                        new QuestionOptionView("option-2", 2, "B", null, null, null, false, null)),
                new QuestionOwnershipView(scope, organizationPublicId, organizationCode,
                        organizationCode, "actor", "Actor", null, null, null, null, false),
                List.of(),
                List.of(),
                Instant.parse("2026-07-30T12:00:00Z"),
                Instant.parse("2026-07-30T12:00:00Z"));
    }
}
