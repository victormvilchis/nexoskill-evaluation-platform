package com.nexoskill.evaluation.questionbank.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
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
import com.nexoskill.evaluation.questionbank.domain.model.QuestionStatus;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class QuestionDuplicateServiceTest {
    private final QuestionBankPort port = mock(QuestionBankPort.class);
    private final QuestionServices.Create create = mock(QuestionServices.Create.class);
    private final TenantContextResolver tenantContextResolver = mock(TenantContextResolver.class);
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final QuestionServices.Duplicate service = new QuestionServices.Duplicate(
            port, create, tenantContextResolver, request);

    @Test
    void globalAdministratorDuplicatesGlobalQuestionAsUnpublishedGlobalContent() {
        TenantContext tenant = TenantContext.organization(20L, "org-20", "ORG_20", true);
        QuestionDetail source = question(ContentScope.GLOBAL, "global", "GLOBAL");
        QuestionDetail copy = question(ContentScope.GLOBAL, "global", "GLOBAL");
        when(port.get(source.publicId())).thenReturn(source);
        when(tenantContextResolver.resolve(request)).thenReturn(tenant);
        when(create.execute(any())).thenReturn(copy);

        assertThat(service.execute(source.publicId(), 7L)).isSameAs(copy);

        ArgumentCaptor<CreateQuestionCommand> command = ArgumentCaptor.forClass(CreateQuestionCommand.class);
        verify(create).execute(command.capture());
        assertThat(command.getValue().contentScope()).isEqualTo("GLOBAL");
        assertThat(command.getValue().organizationPublicId()).isNull();
        assertThat(command.getValue().categoryPublicIds()).containsExactly("category-1");
        assertThat(command.getValue().tags()).containsExactly("herencia");
        assertThat(command.getValue().options()).hasSize(2);
    }


    @Test
    void duplicatesActiveAndArchivedGlobalQuestionsConsecutively() {
        TenantContext tenant = TenantContext.organization(20L, "org-20", "ORG_20", true);
        QuestionDetail active = question(ContentScope.GLOBAL, "global", "GLOBAL", QuestionStatus.ACTIVE);
        QuestionDetail archived = question(ContentScope.GLOBAL, "global", "GLOBAL", QuestionStatus.ARCHIVED);
        QuestionDetail firstCopy = question(ContentScope.GLOBAL, "global", "GLOBAL", QuestionStatus.ACTIVE);
        QuestionDetail secondCopy = question(ContentScope.GLOBAL, "global", "GLOBAL", QuestionStatus.ACTIVE);
        when(port.get(active.publicId())).thenReturn(active, archived);
        when(tenantContextResolver.resolve(request)).thenReturn(tenant);
        when(create.execute(any())).thenReturn(firstCopy, secondCopy);

        assertThat(service.execute(active.publicId(), 7L)).isSameAs(firstCopy);
        assertThat(service.execute(active.publicId(), 7L)).isSameAs(secondCopy);

        verify(create, times(2)).execute(any());
    }

    @Test
    void deletedQuestionCannotBeDuplicated() {
        QuestionDetail deleted = question(ContentScope.GLOBAL, "global", "GLOBAL", QuestionStatus.DELETED);
        when(port.get(deleted.publicId())).thenReturn(deleted);

        assertThatThrownBy(() -> service.execute(deleted.publicId(), 7L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("No se puede duplicar una pregunta eliminada.");

        verify(create, never()).execute(any());
    }

    @Test
    void supervisorDuplicatesOnlyInsideOwnOrganization() {
        TenantContext tenant = TenantContext.organization(20L, "org-20", "ORG_20", false);
        QuestionDetail source = question(ContentScope.ORGANIZATION, "org-20", "ORG_20");
        when(port.get(source.publicId())).thenReturn(source);
        when(tenantContextResolver.resolve(request)).thenReturn(tenant);
        when(create.execute(any())).thenReturn(source);

        service.execute(source.publicId(), 7L);

        ArgumentCaptor<CreateQuestionCommand> command = ArgumentCaptor.forClass(CreateQuestionCommand.class);
        verify(create).execute(command.capture());
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

        verify(create, never()).execute(any());
    }

    private static QuestionDetail question(ContentScope scope, String organizationPublicId,
            String organizationCode) {
        return question(scope, organizationPublicId, organizationCode, QuestionStatus.ACTIVE);
    }

    private static QuestionDetail question(ContentScope scope, String organizationPublicId,
            String organizationCode, QuestionStatus status) {
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
                status,
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
