package com.nexoskill.evaluation.catalogs.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

class AdminCatalogControllerSecurityTest {

    @Test
    void shouldProtectEveryReadEndpointWithCatalogView() {
        Arrays.stream(AdminCatalogController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(GetMapping.class))
                .forEach(method -> assertAuthorization(method, "hasRole('ADMINISTRATOR') or hasAuthority('CATALOG_VIEW')"));
    }

    @Test
    void shouldProtectCreateWithItsSpecificPermission() throws NoSuchMethodException {
        Method method = Arrays.stream(AdminCatalogController.class.getDeclaredMethods())
                .filter(candidate -> candidate.isAnnotationPresent(PostMapping.class))
                .filter(candidate -> Arrays.asList(candidate.getAnnotation(PostMapping.class).value())
                        .contains("/{type}"))
                .findFirst()
                .orElseThrow(NoSuchMethodException::new);

        assertAuthorization(method, "hasRole('ADMINISTRATOR') or hasAnyAuthority('CATALOG_CREATE','CATALOG_MANAGE')");
    }

    @Test
    void shouldProtectUpdateWithItsSpecificPermission() {
        Method method = Arrays.stream(AdminCatalogController.class.getDeclaredMethods())
                .filter(candidate -> candidate.isAnnotationPresent(PutMapping.class))
                .findFirst().orElseThrow();

        assertAuthorization(method, "hasRole('ADMINISTRATOR') or hasAnyAuthority('CATALOG_UPDATE','CATALOG_MANAGE')");
    }

    @Test
    void shouldProtectStatusChangesWithTheirSpecificPermission() {
        Method method = Arrays.stream(AdminCatalogController.class.getDeclaredMethods())
                .filter(candidate -> candidate.isAnnotationPresent(PostMapping.class))
                .filter(candidate -> Arrays.stream(candidate.getAnnotation(PostMapping.class).value())
                        .anyMatch(value -> value.contains("activate|deactivate")))
                .findFirst().orElseThrow();

        assertAuthorization(method, "hasRole('ADMINISTRATOR') or hasAnyAuthority('CATALOG_STATUS_CHANGE','CATALOG_MANAGE')");
    }

    @Test
    void shouldProtectPhysicalDeletionWithItsSpecificPermission() {
        Method method = Arrays.stream(AdminCatalogController.class.getDeclaredMethods())
                .filter(candidate -> candidate.isAnnotationPresent(DeleteMapping.class))
                .findFirst().orElseThrow();

        assertAuthorization(method, "hasRole('ADMINISTRATOR') or hasAnyAuthority('CATALOG_DELETE','CATALOG_MANAGE')");
    }

    private static void assertAuthorization(Method method, String expectedExpression) {
        PreAuthorize authorization = method.getAnnotation(PreAuthorize.class);
        assertThat(authorization)
                .as("La operación %s debe declarar autorización", method.getName())
                .isNotNull();
        assertThat(authorization.value()).isEqualTo(expectedExpression);
    }
}
