package com.nexoskill.evaluation.questionbank.application.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexoskill.evaluation.organizations.domain.model.ContentScope;
import com.nexoskill.evaluation.organizations.domain.model.TenantContext;
import com.nexoskill.evaluation.questionbank.application.model.QuestionDetail;
import com.nexoskill.evaluation.questionbank.application.model.QuestionOwnershipView;
import com.nexoskill.evaluation.questionbank.application.port.out.QuestionBankPort;
import com.nexoskill.evaluation.questionbank.domain.model.QuestionAvailabilityMode;
import com.nexoskill.evaluation.questionbank.domain.model.QuestionStatus;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class QuestionSaveServiceTest {

    private final QuestionServices.Create create = mock(QuestionServices.Create.class);
    private final QuestionServices.Update update = mock(QuestionServices.Update.class);
    private final QuestionAvailabilityService availability = mock(QuestionAvailabilityService.class);
    private final QuestionBankPort questions = mock(QuestionBankPort.class);
    private final QuestionSaveService service = new QuestionSaveService(create, update, availability, questions);
    private final QuestionSaveService.Actor actor = new QuestionSaveService.Actor(7L, "127.0.0.1", "test");

    @Test
    void globalCreationDistributesIndependentCopiesOnlyToSelectedOrganizations() {
        QuestionDetail question = question(ContentScope.GLOBAL);
        TenantContext tenant = TenantContext.global(1L, "global", "GLOBAL");
        when(create.execute(isNull())).thenReturn(question);

        service.create(null, QuestionAvailabilityMode.SELECTED_ORGANIZATIONS,
                List.of("org-20", "org-20", "org-30"), tenant, actor);

        verify(questions).copyGlobalToOrganization(question.publicId(), "org-20", actor.userId());
        verify(questions).copyGlobalToOrganization(question.publicId(), "org-30", actor.userId());
        verify(availability, never()).update(any(), any(), any(), any());
    }

    @Test
    void globalCreationDistributesToEveryActiveCommercialOrganization() {
        QuestionDetail question = question(ContentScope.GLOBAL);
        TenantContext tenant = TenantContext.global(1L, "global", "GLOBAL");
        when(create.execute(isNull())).thenReturn(question);
        when(questions.activeCommercialOrganizationPublicIds()).thenReturn(List.of("org-20", "org-30"));

        service.create(null, QuestionAvailabilityMode.GLOBAL, List.of(), tenant, actor);

        verify(questions).copyGlobalToOrganization(question.publicId(), "org-20", actor.userId());
        verify(questions).copyGlobalToOrganization(question.publicId(), "org-30", actor.userId());
        verify(availability, never()).update(any(), any(), any(), any());
    }

    @Test
    void globalAdministratorCanSaveGlobalAvailabilityFromAnOrganizationalContext() {
        QuestionDetail question = question(ContentScope.GLOBAL);
        TenantContext tenant = TenantContext.organization(20L, "org-20", "ORG_20", true);
        when(update.execute(isNull())).thenReturn(question);

        assertThatCode(() -> service.update(null, QuestionAvailabilityMode.SELECTED_ORGANIZATIONS,
                List.of("org-20"), tenant, actor)).doesNotThrowAnyException();

        verify(availability).update(eq(question.publicId()),
                eq(new QuestionAvailabilityService.UpdateCommand(
                        QuestionAvailabilityMode.SELECTED_ORGANIZATIONS, List.of("org-20"))),
                eq(tenant), any(QuestionAvailabilityService.Actor.class));
    }

    @Test
    void organizationalQuestionRejectsAvailabilityEvenWhenThePayloadIsDesynchronized() {
        when(update.execute(isNull())).thenReturn(question(ContentScope.ORGANIZATION));

        assertThatThrownBy(() -> service.update(null, QuestionAvailabilityMode.GLOBAL, List.of(),
                TenantContext.global(1L, "global", "GLOBAL"), actor))
                .isInstanceOf(BusinessException.class)
                .hasMessage("La disponibilidad organizacional solo puede modificarse sobre una pregunta GLOBAL.");

        verify(availability, never()).update(any(), any(), any(), any());
    }

    @Test
    void organizationalQuestionWithoutAvailabilityPayloadKeepsItsOwnScope() {
        when(create.execute(isNull())).thenReturn(question(ContentScope.ORGANIZATION));

        assertThatCode(() -> service.create(null, null, List.of(),
                TenantContext.organization(20L, "org-20", "ORG_20", false), actor))
                .doesNotThrowAnyException();

        verify(availability, never()).update(any(), any(), any(), any());
    }

    private static QuestionDetail question(ContentScope scope) {
        String organizationPublicId = scope == ContentScope.GLOBAL ? "global" : "org-20";
        String organizationCode = scope == ContentScope.GLOBAL ? "GLOBAL" : "ORG_20";
        return new QuestionDetail(
                "11111111-1111-1111-1111-111111111111",
                "¿Cuál es la respuesta correcta?",
                null,
                "SINGLE_CHOICE",
                "Opción única",
                "JR",
                "JR",
                null,
                null,
                List.of(),
                List.of(),
                QuestionStatus.ACTIVE,
                0L,
                null,
                null,
                null,
                null,
                List.of(),
                new QuestionOwnershipView(scope, organizationPublicId, organizationCode,
                        organizationCode, "actor", "Actor", null, null, null, null, false),
                List.of(),
                List.of(),
                Instant.parse("2026-07-29T12:00:00Z"),
                Instant.parse("2026-07-29T12:00:00Z"));
    }
}
