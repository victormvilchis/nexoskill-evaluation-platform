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
        AuthenticatedUser administrator = actor(Set.of("ADMINISTRATOR"));
        TenantContext organization = TenantContext.organization(10L, "org-public", "ORG", true);

        assertSame(administrator, StudentImportService.certificationImportActor(administrator, organization));
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

    @Test
    void rejectsOrganizationOperatorWhenTenantIsGlobal() {
        AuthenticatedUser supervisor = actor(Set.of("SUPERVISOR"));

        assertThrows(BusinessException.class,
                () -> StudentImportService.certificationImportActor(supervisor,
                        TenantContext.global(1L, "global-public", "GLOBAL")));
    }

    private AuthenticatedUser actor(Set<String> roles) {
        return new AuthenticatedUser(7L, "actor-public", "actor@nexoskill.local", "Actor", "Prueba",
                "Actor Prueba", roles, Set.of("STUDENT_CREATE", "STUDENT_UPDATE"), null,
                null, null, null, false, null, null);
    }
}
