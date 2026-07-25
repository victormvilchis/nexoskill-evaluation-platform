package com.nexoskill.evaluation.users.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import com.nexoskill.evaluation.users.application.model.AdminUserSummary;
import com.nexoskill.evaluation.users.application.model.DeleteUserCommand;
import com.nexoskill.evaluation.users.application.port.out.UserManagementPort;
import com.nexoskill.evaluation.users.application.port.out.UserSessionPort;
import com.nexoskill.evaluation.users.domain.model.UserAccessStatus;
import com.nexoskill.evaluation.users.domain.model.UserStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DeleteUserServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-24T20:00:00Z");

    @Test
    void rejectsDeletingOwnAccount() {
        UserManagementPort users = mock(UserManagementPort.class);
        UserSessionPort sessions = mock(UserSessionPort.class);
        AuditLogPort audit = mock(AuditLogPort.class);
        DeleteUserService service = new DeleteUserService(users, sessions, audit,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(users.getByPublicId("self")).thenReturn(new UserManagementPort.ManagedUser(10L, summary("self")));

        assertThatThrownBy(() -> service.delete(new DeleteUserCommand("self", null, 10L, "127.0.0.1", "JUnit")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("No puedes eliminar tu propia cuenta.");
        verifyNoInteractions(sessions, audit);
    }

    @Test
    void rejectsDeletingLastEffectiveAdministrator() {
        UserManagementPort users = mock(UserManagementPort.class);
        UserSessionPort sessions = mock(UserSessionPort.class);
        AuditLogPort audit = mock(AuditLogPort.class);
        DeleteUserService service = new DeleteUserService(users, sessions, audit,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(users.getByPublicId("admin")).thenReturn(new UserManagementPort.ManagedUser(20L, summary("admin")));
        when(users.countEffectiveAdministrators(NOW)).thenReturn(1L);

        assertThatThrownBy(() -> service.delete(new DeleteUserCommand("admin", null, 10L, "127.0.0.1", "JUnit")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("No puedes eliminar al último administrador activo de la plataforma.");
        verifyNoInteractions(sessions, audit);
    }

    private AdminUserSummary summary(String id) {
        return new AdminUserSummary(id, id + "@nexoskill.local", "Admin", "NexoSkill", "Admin NexoSkill",
                UserStatus.ACTIVE, Set.of("ADMINISTRATOR"), UserAccessStatus.ACTIVE,
                NOW.minusSeconds(60), null, null);
    }
}
