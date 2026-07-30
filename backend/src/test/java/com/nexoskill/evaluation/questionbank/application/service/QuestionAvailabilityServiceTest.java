package com.nexoskill.evaluation.questionbank.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.organizations.domain.model.TenantContext;
import com.nexoskill.evaluation.questionbank.domain.model.QuestionAvailabilityMode;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class QuestionAvailabilityServiceTest {

    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final AuditLogPort audit = mock(AuditLogPort.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-29T12:00:00Z"), ZoneOffset.UTC);
    private final QuestionAvailabilityService service = new QuestionAvailabilityService(jdbc, audit, clock);
    private final QuestionAvailabilityService.Actor actor =
            new QuestionAvailabilityService.Actor(7L, "127.0.0.1", "test");

    @Test
    void availabilityModeIsRequiredAndNeverDefaultsToGlobalPublication() {
        assertThatThrownBy(() -> service.update(
                "11111111-1111-1111-1111-111111111111",
                new QuestionAvailabilityService.UpdateCommand(null, List.of()),
                TenantContext.global(1L, "global", "GLOBAL"), actor))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Selecciona una configuración de disponibilidad válida.");

        verifyNoInteractions(jdbc, audit);
    }

    @Test
    void organizationalUserCannotChangeGlobalAvailability() {
        assertThatThrownBy(() -> service.update(
                "11111111-1111-1111-1111-111111111111",
                new QuestionAvailabilityService.UpdateCommand(QuestionAvailabilityMode.NONE, List.of()),
                TenantContext.organization(20L, "org-20", "ORG_20", false), actor))
                .isInstanceOf(BusinessException.class)
                .hasMessage("La disponibilidad organizacional solo puede modificarse por un Administrador global sobre una pregunta global.");

        verifyNoInteractions(jdbc, audit);
    }

    @Test
    void availabilityChangeRequiresAnAuthenticatedActor() {
        assertThatThrownBy(() -> service.update(
                "11111111-1111-1111-1111-111111111111",
                new QuestionAvailabilityService.UpdateCommand(QuestionAvailabilityMode.NONE, List.of()),
                TenantContext.global(1L, "global", "GLOBAL"), null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("No fue posible identificar al usuario que realiza la operación.");

        verifyNoInteractions(jdbc, audit);
    }
}
