package com.nexoskill.evaluation.students.application;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nexoskill.evaluation.authentication.infrastructure.security.AuthenticatedUser;
import com.nexoskill.evaluation.organizations.domain.model.TenantContext;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StudentImportServiceAuthorizationTest {

    @Test
    void allowsGlobalAdministratorWithSelectedCommercialOrganization() {
        AuthenticatedUser administrator = actor(Set.of("ADMINISTRATOR"), Set.of("STUDENT_IMPORT"));
        TenantContext organization = TenantContext.organization(10L, "org-public", "ORG", true);

        assertSame(administrator, StudentImportService.certificationImportActor(administrator, organization));
    }

    @Test
    void leavesOrganizationOperatorUnchanged() {
        AuthenticatedUser supervisor = actor(Set.of("SUPERVISOR"), Set.of("STUDENT_IMPORT"));

        assertSame(supervisor, StudentImportService.certificationImportActor(
                supervisor, TenantContext.organization(10L, "org-public", "ORG", false)));
    }

    @Test
    void allowsCustomRoleWithImportPermissionInItsOrganization() {
        AuthenticatedUser customRole = actor(Set.of("CUSTOM_IMPORTER"), Set.of("STUDENT_IMPORT"));

        assertSame(customRole, StudentImportService.certificationImportActor(
                customRole, TenantContext.organization(10L, "org-public", "ORG", false)));
    }

    @Test
    void rejectsGlobalAdministratorWithoutSelectedOrganization() {
        AuthenticatedUser administrator = actor(Set.of("ADMINISTRATOR"), Set.of("STUDENT_IMPORT"));

        assertThrows(BusinessException.class,
                () -> StudentImportService.certificationImportActor(administrator, null));
    }

    @Test
    void rejectsOrganizationOperatorWhenTenantIsGlobal() {
        AuthenticatedUser supervisor = actor(Set.of("SUPERVISOR"), Set.of("STUDENT_IMPORT"));

        assertThrows(BusinessException.class,
                () -> StudentImportService.certificationImportActor(supervisor,
                        TenantContext.global(1L, "global-public", "GLOBAL")));
    }

    private AuthenticatedUser actor(Set<String> roles, Set<String> permissions) {
        return new AuthenticatedUser(7L, "actor-public", "actor@nexoskill.local", "Actor", "Prueba",
                "Actor Prueba", roles, permissions, null,
                null, null, null, false, null, null);
    }
}
