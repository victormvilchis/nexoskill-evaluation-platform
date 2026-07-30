package com.nexoskill.evaluation.shared.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.nexoskill.evaluation.authentication.infrastructure.security.PlatformAccessDeniedHandler;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;

class GlobalExceptionHandlerTest {
    @Test
    void preservesStructuredFieldErrorsFromBusinessValidation() {
        HttpServletRequest request = org.mockito.Mockito.mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/admin/students");
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        var response = handler.handleBusiness(new BusinessException("STUDENT_EMAIL_EXISTS",
                "El correo ya está registrado.",
                Map.of("email", "Ya existe un estudiante con este correo dentro de la organización.")), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().fieldErrors()).containsEntry("email",
                "Ya existe un estudiante con este correo dentro de la organización.");
    }
    @Test
    void usesTheCentralizedAuthorizationDenialContract() {
        HttpServletRequest request = org.mockito.Mockito.mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/admin/forms");
        PlatformAccessDeniedHandler denialHandler = org.mockito.Mockito.mock(PlatformAccessDeniedHandler.class);
        when(denialHandler.resolveAndAudit(request)).thenReturn(new PlatformAccessDeniedHandler.Denial(
                "ORGANIZATIONAL_MODULE_READ_ONLY",
                "Tu rol tiene acceso de consulta a este módulo, pero no permite realizar modificaciones."));
        GlobalExceptionHandler handler = new GlobalExceptionHandler(denialHandler);

        var response = handler.handleAccessDenied(new AccessDeniedException("denied"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("ORGANIZATIONAL_MODULE_READ_ONLY");
    }

}
