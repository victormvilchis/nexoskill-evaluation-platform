package com.nexoskill.evaluation.questionbank.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import com.nexoskill.evaluation.organizations.application.TenantContextResolver;
import com.nexoskill.evaluation.organizations.domain.model.ContentScope;
import com.nexoskill.evaluation.organizations.domain.model.TenantContext;
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

class QuestionDuplicateServiceTest {
    private final QuestionBankPort port = mock(QuestionBankPort.class);
    private final TenantContextResolver tenantContextResolver = mock(TenantContextResolver.class);
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final QuestionServices.Duplicate service = new QuestionServices.Duplicate(
            port, tenantContextResolver, request);

    @Test
    void globalAdministratorDuplicatesGlobalQuestionAsUnpublishedGlobalContent() {
        TenantContext tenant = TenantContext.organization(20L, "org-20", "ORG_20", true);
        QuestionDetail source = question(ContentScope.GLOBAL, "global", "GLOBAL");
        QuestionDetail copy = question(ContentScope.GLOBAL, "global", "GLOBAL");
        when(port.get(source.publicId())).thenReturn(source);
        when(tenantContextResolver.resolve(request)).thenReturn(tenant);
        when(port.duplicate(source.publicId(), 7L)).thenReturn(copy);

        assertThat(service.execute(source.publicId(), 7L)).isSameAs(copy);

        verify(port).duplicate(source.publicId(), 7L);
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
        when(port.duplicate(active.publicId(), 7L)).thenReturn(firstCopy, secondCopy);

        assertThat(service.execute(active.publicId(), 7L)).isSameAs(firstCopy);
        assertThat(service.execute(active.publicId(), 7L)).isSameAs(secondCopy);

        verify(port, times(2)).duplicate(active.publicId(), 7L);
    }

    @Test
    void globalAdministratorDuplicatesGlobalQuestionIntoSelectedOrganization() {
        TenantContext tenant = TenantContext.global(1L, "global", "GLOBAL");
        QuestionDetail source = question(ContentScope.GLOBAL, "global", "GLOBAL");
        QuestionDetail copy = question(ContentScope.ORGANIZATION, "org-20", "ORG_20");
        when(port.get(source.publicId())).thenReturn(source);
        when(tenantContextResolver.resolve(request)).thenReturn(tenant);
        when(port.duplicateGlobalToOrganization(source.publicId(), "org-20", 7L)).thenReturn(copy);

        assertThat(service.execute(source.publicId(), "ORGANIZATION", "org-20", 7L)).isSameAs(copy);

        verify(port).duplicateGlobalToOrganization(source.publicId(), "org-20", 7L);
    }

    @Test
    void nonAdministratorCannotSelectAnotherDuplicateDestination() {
        TenantContext tenant = TenantContext.organization(20L, "org-20", "ORG_20", false);
        QuestionDetail source = question(ContentScope.ORGANIZATION, "org-20", "ORG_20");
        when(port.get(source.publicId())).thenReturn(source);
        when(tenantContextResolver.resolve(request)).thenReturn(tenant);

        assertThatThrownBy(() -> service.execute(source.publicId(), "ORGANIZATION", "org-21", 7L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Solo el Administrador global puede seleccionar otro destino para la duplicación.");

        verify(port, never()).duplicateGlobalToOrganization(source.publicId(), "org-21", 7L);
    }

    @Test
    void deletedQuestionCannotBeDuplicated() {
        QuestionDetail deleted = question(ContentScope.GLOBAL, "global", "GLOBAL", QuestionStatus.DELETED);
        when(port.get(deleted.publicId())).thenReturn(deleted);

        assertThatThrownBy(() -> service.execute(deleted.publicId(), 7L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("No se puede duplicar una pregunta eliminada.");

        verify(port, never()).duplicate(deleted.publicId(), 7L);
    }

    @Test
    void supervisorDuplicatesOnlyInsideOwnOrganization() {
        TenantContext tenant = TenantContext.organization(20L, "org-20", "ORG_20", false);
        QuestionDetail source = question(ContentScope.ORGANIZATION, "org-20", "ORG_20");
        when(port.get(source.publicId())).thenReturn(source);
        when(tenantContextResolver.resolve(request)).thenReturn(tenant);
        when(port.duplicate(source.publicId(), 7L)).thenReturn(source);

        service.execute(source.publicId(), 7L);

        verify(port).duplicate(source.publicId(), 7L);
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

        verify(port, never()).duplicate(source.publicId(), 7L);
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
