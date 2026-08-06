package com.nexoskill.evaluation.roles.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.users.infrastructure.persistence.PermissionJpaEntity;
import com.nexoskill.evaluation.users.infrastructure.persistence.RoleJpaEntity;
import com.nexoskill.evaluation.users.infrastructure.persistence.SpringDataPermissionJpaRepository;
import com.nexoskill.evaluation.users.infrastructure.persistence.SpringDataRoleJpaRepository;
import com.nexoskill.evaluation.users.infrastructure.persistence.SpringDataUserJpaRepository;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class RoleManagementServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-05T18:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void shouldExposeOnlyOrganizationalPermissionsAndKeepBasicsReadOnly() {
        Fixture fixture = fixture();

        RoleManagementService.PermissionCatalog catalog = fixture.service.permissionCatalog("ORGANIZATIONAL");

        Set<String> visible = catalog.modules().stream()
                .flatMap(module -> module.permissions().stream())
                .map(RoleManagementService.PermissionDescriptor::code)
                .collect(java.util.stream.Collectors.toSet());
        assertThat(visible).contains("PROFILE_VIEW", "PASSWORD_CHANGE", "STUDENT_VIEW", "STUDENT_IMPORT")
                .doesNotContain("ORGANIZATION_VIEW", "USER_VIEW", "ROLE_MANAGE", "GLOBAL_CONTENT_PROMOTE");
        assertThat(catalog.modules().stream()
                .flatMap(module -> module.permissions().stream())
                .filter(permission -> permission.code().equals("PROFILE_VIEW")
                        || permission.code().equals("PASSWORD_CHANGE"))
                .allMatch(RoleManagementService.PermissionDescriptor::required)).isTrue();
    }

    @Test
    void shouldIgnoreAdministratorPermissionsAndCompleteViewDependencyWhenCreatingRole() {
        Fixture fixture = fixture();
        when(fixture.roles.countByNormalizedName("Operador", null)).thenReturn(0L);
        when(fixture.roles.saveAndFlush(any(RoleJpaEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RoleManagementService.RoleDetail created = fixture.service.create(
                new RoleManagementService.UpsertCommand("Operador", null,
                        Set.of("ORGANIZATION_VIEW", "USER_VIEW", "ROLE_MANAGE", "STUDENT_IMPORT")),
                actor());

        assertThat(created.permissionCodes())
                .contains("DASHBOARD_VIEW", "USER_PANEL_VIEW", "PROFILE_VIEW", "PASSWORD_CHANGE",
                        "STUDENT_VIEW", "STUDENT_IMPORT")
                .doesNotContain("ORGANIZATION_VIEW", "USER_VIEW", "ROLE_MANAGE");
    }

    @Test
    void shouldRemoveLegacyGlobalPermissionsWhenUpdatingRole() {
        Fixture fixture = fixture();
        RoleJpaEntity role = RoleJpaEntity.custom("MANAGER", "Gestor", null,
                Set.of(permission("ORGANIZATION_VIEW", "Ver organizaciones", "ORGANIZATIONS"),
                        permission("ROLE_MANAGE", "Administrar roles", "ROLE_MANAGEMENT"),
                        permission("STUDENT_VIEW", "Ver colaboradores", "STUDENTS")), NOW);
        setField(role, "id", 20L);
        when(fixture.roles.findByCodeIgnoreCase("MANAGER")).thenReturn(Optional.of(role));
        when(fixture.roles.countByNormalizedName("Gestor", 20L)).thenReturn(0L);
        when(fixture.roles.saveAndFlush(role)).thenReturn(role);
        when(fixture.users.findUserIdsAssignedToRole(20L)).thenReturn(List.of());

        RoleManagementService.RoleDetail updated = fixture.service.update("MANAGER",
                new RoleManagementService.UpsertCommand("Gestor", null, Set.of("TALENT_CONVERT")), actor());

        assertThat(updated.permissionCodes())
                .contains("TALENT_VIEW", "TALENT_CONVERT", "PROFILE_VIEW", "PASSWORD_CHANGE")
                .doesNotContain("ORGANIZATION_VIEW", "ROLE_MANAGE", "STUDENT_VIEW");
    }

    @Test
    void shouldCloneOnlyApplicableOrganizationalPermissions() {
        Fixture fixture = fixture();
        RoleJpaEntity source = RoleJpaEntity.custom("SUPERVISOR", "Supervisor", null,
                Set.of(permission("ORGANIZATION_VIEW", "Ver organizaciones", "ORGANIZATIONS"),
                        permission("QUESTION_UPDATE", "Editar preguntas", "QUESTION_BANK")), NOW);
        setField(source, "id", 30L);
        when(fixture.roles.findByCodeIgnoreCase("SUPERVISOR")).thenReturn(Optional.of(source));
        when(fixture.roles.countByNormalizedName("Supervisor copia", null)).thenReturn(0L);
        when(fixture.roles.saveAndFlush(any(RoleJpaEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RoleManagementService.RoleDetail cloned = fixture.service.cloneRole("SUPERVISOR",
                new RoleManagementService.CloneCommand("Supervisor copia", null), actor());

        assertThat(cloned.permissionCodes())
                .contains("QUESTION_VIEW", "QUESTION_UPDATE", "PROFILE_VIEW", "PASSWORD_CHANGE")
                .doesNotContain("ORGANIZATION_VIEW");
    }

    private static Fixture fixture() {
        SpringDataRoleJpaRepository roles = mock(SpringDataRoleJpaRepository.class);
        SpringDataPermissionJpaRepository permissions = mock(SpringDataPermissionJpaRepository.class);
        SpringDataUserJpaRepository users = mock(SpringDataUserJpaRepository.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AuditLogPort audit = mock(AuditLogPort.class);
        List<PermissionJpaEntity> all = List.of(
                permission("DASHBOARD_VIEW", "Ver inicio", "DASHBOARD"),
                permission("USER_PANEL_VIEW", "Ver panel", "USER"),
                permission("PROFILE_VIEW", "Consultar perfil", "PROFILE"),
                permission("PASSWORD_CHANGE", "Cambiar contraseña", "PROFILE"),
                permission("PROFILE_UPDATE", "Actualizar perfil propio", "PROFILE"),
                permission("STUDENT_VIEW", "Ver colaboradores", "STUDENTS"),
                permission("STUDENT_IMPORT", "Importar colaboradores", "STUDENTS"),
                permission("TALENT_VIEW", "Ver Talent Bank", "TALENT_BANK"),
                permission("TALENT_CONVERT", "Convertir a colaborador", "TALENT_BANK"),
                permission("QUESTION_VIEW", "Ver preguntas", "QUESTION_BANK"),
                permission("QUESTION_UPDATE", "Editar preguntas", "QUESTION_BANK"),
                permission("ORGANIZATION_VIEW", "Ver organizaciones", "ORGANIZATIONS"),
                permission("USER_VIEW", "Ver usuarios", "USER_MANAGEMENT"),
                permission("ROLE_MANAGE", "Administrar roles", "ROLE_MANAGEMENT"),
                permission("GLOBAL_CONTENT_PROMOTE", "Promover contenido", "GLOBAL_CONTENT"));
        when(permissions.findAll()).thenReturn(all);
        when(permissions.findAllByOrderByModuleCodeAscNameAsc()).thenReturn(all);
        return new Fixture(roles, permissions, users,
                new RoleManagementService(roles, permissions, users, jdbc, audit, CLOCK));
    }

    private static PermissionJpaEntity permission(String code, String name, String module) {
        PermissionJpaEntity permission;
        try {
            var constructor = PermissionJpaEntity.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            permission = constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
        setField(permission, "code", code);
        setField(permission, "name", name);
        setField(permission, "moduleCode", module);
        setField(permission, "description", name);
        return permission;
    }

    private static RoleManagementService.Actor actor() {
        return new RoleManagementService.Actor(1L, "127.0.0.1", "test");
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private record Fixture(SpringDataRoleJpaRepository roles,
            SpringDataPermissionJpaRepository permissions,
            SpringDataUserJpaRepository users,
            RoleManagementService service) {
    }
}
