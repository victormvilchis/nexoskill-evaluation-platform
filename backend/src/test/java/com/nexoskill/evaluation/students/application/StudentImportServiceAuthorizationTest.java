package com.nexoskill.evaluation.students.application;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nexoskill.evaluation.authentication.infrastructure.security.AuthenticatedUser;
import com.nexoskill.evaluation.organizations.domain.model.TenantContext;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StudentImportServiceAuthorizationTest {

    @Test
    void adaptsGlobalAdministratorOnlyForOrganizationScopedCertificationImport() {
        AuthenticatedUser administrator = actor(Set.of("ADMINISTRATOR"));
        TenantContext organization = TenantContext.organization(10L, "org-public", "ORG", true);

        AuthenticatedUser adapted = StudentImportService.certificationImportActor(administrator, organization);

        assertTrue(adapted.roles().contains("ADMINISTRATOR"));
        assertTrue(adapted.roles().contains("MANAGER"));
        assertSame(administrator.permissions(), adapted.permissions());
    }

    @Test
    void leavesOrganizationOperatorUnchanged() {
        AuthenticatedUser supervisor = actor(Set.of("SUPERVISOR"));

        assertSame(supervisor, StudentImportService.certificationImportActor(
                supervisor, TenantContext.organization(10L, "org-public", "ORG", false)));
    }

    @Test
    void rejectsGlobalAdministratorWithoutSelectedOrganization() {
        AuthenticatedUser administrator = actor(Set.of("ADMINISTRATOR"));

        assertThrows(BusinessException.class,
                () -> StudentImportService.certificationImportActor(administrator, null));
    }

    private AuthenticatedUser actor(Set<String> roles) {
        return new AuthenticatedUser(7L, "actor-public", "actor@nexoskill.local", "Actor", "Prueba",
                "Actor Prueba", roles, Set.of("STUDENT_CREATE", "STUDENT_UPDATE"), null,
                null, null, null, false, null, null);
    }
}
