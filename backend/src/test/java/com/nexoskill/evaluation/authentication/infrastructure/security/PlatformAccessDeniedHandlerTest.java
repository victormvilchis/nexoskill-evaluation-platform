package com.nexoskill.evaluation.authentication.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.users.domain.model.UserAccessStatus;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class PlatformAccessDeniedHandlerTest {
	private final AuditLogPort audit = mock(AuditLogPort.class);
	private final Clock clock = Clock.fixed(Instant.parse("2026-07-29T18:00:00Z"), ZoneOffset.UTC);
	private final PlatformAccessDeniedHandler handler = new PlatformAccessDeniedHandler(audit, clock);

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void returnsSpecificReadOnlyMessageWhenManagerAttemptsCatalogWrite() {
		AuthenticatedUser user = user("MANAGER");
		SecurityContextHolder.getContext()
				.setAuthentication(new UsernamePasswordAuthenticationToken(user, null, Set.of()));
		HttpServletRequest request = request("POST", "/api/v1/admin/catalogs/TECHNOLOGIES");

		PlatformAccessDeniedHandler.Denial denial = handler.resolveAndAudit(request);

		assertThat(denial.code()).isEqualTo("ORGANIZATIONAL_MODULE_READ_ONLY");
		assertThat(denial.message()).contains("acceso de consulta");
		verify(audit).record(eq(10L), eq("AUTHORIZATION_DENIED"), eq("SECURITY"),
				eq("Se rechazó una operación sin permisos efectivos."), eq("127.0.0.1"), eq("JUnit"),
				org.mockito.ArgumentMatchers.anyMap(), eq(clock.instant()));
	}

	@Test
	void returnsCertificationMessageWhenGlobalAdministratorAttemptsOperationalManagement() {
		AuthenticatedUser user = user("ADMINISTRATOR");
		SecurityContextHolder.getContext()
				.setAuthentication(new UsernamePasswordAuthenticationToken(user, null, Set.of()));

		PlatformAccessDeniedHandler.Denial denial = handler
				.resolveAndAudit(request("PUT", "/api/v1/admin/students/student-id/certifications"));

		assertThat(denial.code()).isEqualTo("CERTIFICATION_OPERATION_FORBIDDEN");
		assertThat(denial.message()).contains("Gestores y Supervisores");
	}

	@Test
	void keepsGenericDenialForSupervisorWithoutPermission() {
		AuthenticatedUser user = user("SUPERVISOR");
		SecurityContextHolder.getContext()
				.setAuthentication(new UsernamePasswordAuthenticationToken(user, null, Set.of()));

		PlatformAccessDeniedHandler.Denial denial = handler
				.resolveAndAudit(request("POST", "/api/v1/admin/organizations"));

		assertThat(denial.code()).isEqualTo("ACCESS_DENIED");
	}

	private HttpServletRequest request(String method, String path) {
		HttpServletRequest request = mock(HttpServletRequest.class);
		when(request.getMethod()).thenReturn(method);
		when(request.getRequestURI()).thenReturn(path);
		when(request.getRemoteAddr()).thenReturn("127.0.0.1");
		when(request.getHeader("User-Agent")).thenReturn("JUnit");
		return request;
	}

	private AuthenticatedUser user(String role) {
		return new AuthenticatedUser(10L, "user-public-id", "user@nexoskill.test", "User", "Test", "User Test",
				Set.of(role), Set.of(), null, UserAccessStatus.ACTIVE, null, null, false, null, null);
	}
}
