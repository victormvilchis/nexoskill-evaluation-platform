package com.nexoskill.evaluation.certifications.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

class StudentCertificationControllerSecurityTest {

    @Test
    void shouldAuthorizeConsultationEndpointsWithStudentView() {
        Arrays.stream(StudentCertificationController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(GetMapping.class))
                .forEach(method -> assertAuthorization(method, "hasAuthority('STUDENT_VIEW')"));
    }

    @Test
    void shouldKeepMutationEndpointsProtectedByCertificationManagementPermission() {
        Arrays.stream(StudentCertificationController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(PostMapping.class)
                        || method.isAnnotationPresent(PutMapping.class))
                .forEach(method -> assertAuthorization(method,
                        "hasAuthority('STUDENT_CERTIFICATION_MANAGE')"));
    }

    private static void assertAuthorization(Method method, String expectedExpression) {
        PreAuthorize authorization = method.getAnnotation(PreAuthorize.class);
        assertThat(authorization)
                .as("La operación %s debe declarar autorización", method.getName())
                .isNotNull();
        assertThat(authorization.value()).isEqualTo(expectedExpression);
    }
}
