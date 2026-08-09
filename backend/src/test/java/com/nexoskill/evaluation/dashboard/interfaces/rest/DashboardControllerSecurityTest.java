package com.nexoskill.evaluation.dashboard.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;

class DashboardControllerSecurityTest {

    @Test
    void shouldProtectDashboardConsultationWithViewPermissionOrAdministratorRole() {
        for (Method method : DashboardController.class.getDeclaredMethods()) {
            if (!method.isAnnotationPresent(GetMapping.class)) continue;
            PreAuthorize authorization = method.getAnnotation(PreAuthorize.class);
            assertThat(authorization).as("La operación %s debe declarar autorización", method.getName()).isNotNull();
            assertThat(authorization.value()).isEqualTo("hasRole('ADMINISTRATOR') or hasAuthority('DASHBOARD_VIEW')");
        }
    }

    @Test
    void shouldProtectPersonalizationWithSpecificPermissionOrAdministratorRole() {
        for (Method method : DashboardController.class.getDeclaredMethods()) {
            if (!method.isAnnotationPresent(PutMapping.class)) continue;
            PreAuthorize authorization = method.getAnnotation(PreAuthorize.class);
            assertThat(authorization).as("La operación %s debe declarar autorización", method.getName()).isNotNull();
            assertThat(authorization.value()).isEqualTo("hasRole('ADMINISTRATOR') or (hasAuthority('DASHBOARD_VIEW') and hasAuthority('DASHBOARD_PERSONALIZE'))");
        }
    }
}
