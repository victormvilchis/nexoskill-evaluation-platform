package com.nexoskill.evaluation.users.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import com.nexoskill.evaluation.users.application.model.AdminUserSummary;
import com.nexoskill.evaluation.users.application.model.UserStatusCommand;
import com.nexoskill.evaluation.users.application.port.out.UserManagementPort;
import com.nexoskill.evaluation.users.application.port.out.UserSessionPort;
import com.nexoskill.evaluation.users.domain.model.UserAccessStatus;
import com.nexoskill.evaluation.users.domain.model.UserStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SuspendUserServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-24T20:00:00Z");

    @Test
    void shouldPreventAdministratorFromSuspendingOwnAccount() {
        UserManagementPort userManagementPort = mock(UserManagementPort.class);
        UserSessionPort userSessionPort = mock(UserSessionPort.class);
        AuditLogPort auditLogPort = mock(AuditLogPort.class);
        SuspendUserService service = new SuspendUserService(
                userManagementPort,
                userSessionPort,
                auditLogPort,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        AdminUserSummary summary = new AdminUserSummary(
                "public-admin",
                "admin@nexoskill.local",
                "Administrador",
                "NexoSkill",
                "Administrador NexoSkill",
                UserStatus.ACTIVE,
                Set.of("ADMINISTRATOR"),
                UserAccessStatus.ACTIVE,
                NOW.minusSeconds(60),
                null,
                null
        );

        when(userManagementPort.getByPublicId("public-admin"))
                .thenReturn(new UserManagementPort.ManagedUser(10L, summary));

        UserStatusCommand command = new UserStatusCommand(
                "public-admin",
                10L,
                "127.0.0.1",
                "JUnit"
        );

        assertThatThrownBy(() -> service.suspend(command))
                .isInstanceOf(BusinessException.class)
                .hasMessage("No puedes suspender tu propia cuenta.");

        verifyNoInteractions(userSessionPort, auditLogPort);
    }
}
