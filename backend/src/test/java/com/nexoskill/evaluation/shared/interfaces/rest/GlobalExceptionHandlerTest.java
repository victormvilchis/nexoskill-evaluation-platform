package com.nexoskill.evaluation.shared.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.nexoskill.evaluation.shared.domain.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

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
}
