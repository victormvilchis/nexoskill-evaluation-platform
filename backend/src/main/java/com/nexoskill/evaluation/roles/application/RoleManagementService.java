package com.nexoskill.evaluation.roles.application;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import com.nexoskill.evaluation.users.infrastructure.persistence.PermissionJpaEntity;
import com.nexoskill.evaluation.users.infrastructure.persistence.RoleJpaEntity;
import com.nexoskill.evaluation.users.infrastructure.persistence.SpringDataPermissionJpaRepository;
import com.nexoskill.evaluation.users.infrastructure.persistence.SpringDataRoleJpaRepository;
import com.nexoskill.evaluation.users.infrastructure.persistence.SpringDataUserJpaRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleManagementService {

    private static final String STUDENT_PORTAL_ROLE = "USER";
    private static final Set<String> BASE_PERMISSIONS = Set.of(
            "DASHBOARD_VIEW", "USER_PANEL_VIEW");
    private static final Set<String> NON_APPLICABLE_PERMISSIONS = Set.of(
            "QUESTION_REVIEW", "QUESTION_PUBLISH", "QUESTION_VERSION_VIEW",
            "STUDENT_CERTIFICATION_CATALOG_VIEW");
    private static final Set<String> ADMINISTRATOR_EXCLUSIVE_PERMISSIONS = Set.of(
            "ROLE_MANAGE", "ADMIN_PANEL_VIEW", "TENANT_CONTEXT_SELECT",
            "USER_VIEW", "USER_CREATE", "USER_UPDATE", "USER_STATUS_CHANGE", "USER_PASSWORD_RESET",
            "USER_ACCESS_MANAGE", "USER_ROLE_ASSIGN",
            "ORGANIZATION_VIEW", "ORGANIZATION_CREATE", "ORGANIZATION_UPDATE",
            "ORGANIZATION_STATUS_CHANGE", "ORGANIZATION_LICENSE_VIEW", "ORGANIZATION_LICENSE_UPDATE",
            "GLOBAL_CONTENT_DISTRIBUTE", "GLOBAL_CONTENT_PROMOTE", "GLOBAL_CONTENT_PUBLISH",
            "GLOBAL_CONTENT_REVIEW", "GLOBAL_CONTENT_SYNCHRONIZE", "GLOBAL_CONTENT_VERSION_MANAGE");

    private static final Map<String, String> MODULE_NAMES = Map.ofEntries(
            Map.entry("DASHBOARD", "Inicio"),
            Map.entry("PROFILE", "Perfil"),
            Map.entry("USER_MANAGEMENT", "Usuarios"),
            Map.entry("ORGANIZATIONS", "Organizaciones"),
            Map.entry("STUDENTS", "Colaboradores"),
            Map.entry("TALENT_BANK", "Talent Bank"),
            Map.entry("CATALOGS", "Catálogos"),
            Map.entry("QUESTION_BANK", "Banco de preguntas"),
            Map.entry("FORMS", "Formularios"),
            Map.entry("GLOBAL_CONTENT", "Contenido global"),
            Map.entry("ROLE_MANAGEMENT", "Roles"),
            Map.entry("ADMIN", "Administración"));

    private final SpringDataRoleJpaRepository roles;
    private final SpringDataPermissionJpaRepository permissions;
    private final SpringDataUserJpaRepository users;
    private final JdbcTemplate jdbc;
    private final AuditLogPort audit;
    private final Clock clock;

    public RoleManagementService(SpringDataRoleJpaRepository roles,
            SpringDataPermissionJpaRepository permissions,
            SpringDataUserJpaRepository users, JdbcTemplate jdbc,
            AuditLogPort audit, Clock clock) {
        this.roles = roles;
        this.permissions = permissions;
        this.users = users;
        this.jdbc = jdbc;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<RoleSummary> list(String query, String requestedStatus, Pageable pageable) {
        String status = normalizeStatusFilter(requestedStatus);
        String normalizedQuery = query == null || query.isBlank()
                ? null : query.trim().toLowerCase(Locale.ROOT);
        return roles.search(normalizedQuery, "ALL".equals(status) ? null : status, pageable)
                .map(this::summary);
    }

    @Transactional(readOnly = true)
    public RoleDetail get(String roleCode) {
        RoleJpaEntity role = requireRole(roleCode);
        if (STUDENT_PORTAL_ROLE.equals(role.getCode())) {
            throw new BusinessException("ROLE_NOT_FOUND", "El rol solicitado no existe.");
        }
        return detail(role);
    }

    @Transactional(readOnly = true)
    public PermissionCatalog permissionCatalog(String requestedScope) {
        boolean administratorScope = "ADMINISTRATOR".equals(normalizeCatalogScope(requestedScope));
        List<PermissionJpaEntity> visible = permissions.findAllByOrderByModuleCodeAscNameAsc().stream()
                .filter(permission -> !"USER".equals(permission.getModuleCode()))
                .filter(permission -> !NON_APPLICABLE_PERMISSIONS.contains(permission.getCode()))
                .filter(permission -> administratorScope || isOrganizationalPermission(permission))
                .toList();

        Map<String, List<PermissionJpaEntity>> grouped = visible.stream()
                .collect(Collectors.groupingBy(this::logicalModuleCode, LinkedHashMap::new, Collectors.toList()));

        List<PermissionModule> modules = grouped.entrySet().stream()
                .map(entry -> new PermissionModule(entry.getKey(), moduleName(entry.getKey()),
                        entry.getValue().stream()
                                .sorted(Comparator.comparingInt(this::permissionOrder)
                                        .thenComparing(PermissionJpaEntity::getName))
                                .map(this::descriptor)
                                .toList()))
                .sorted(Comparator.comparingInt(module -> moduleOrder(module.code())))
                .toList();
        return new PermissionCatalog(modules);
    }

    @Transactional
    public RoleDetail create(UpsertCommand command, Actor actor) {
        String name = normalizedName(command.name());
        assertUniqueName(name, null);
        Set<PermissionJpaEntity> granted = resolvePermissions(command.permissionCodes());
        Instant now = clock.instant();
        String code = "CUSTOM_" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
        RoleJpaEntity created = roles.saveAndFlush(RoleJpaEntity.custom(code, name,
                normalizeDescription(command.description()), granted, now));
        record(actor, "ROLE_CREATED", "Se creó un rol configurable.", created,
                Map.of("permissionCount", granted.size()));
        return detail(created);
    }

    @Transactional
    public RoleDetail update(String roleCode, UpsertCommand command, Actor actor) {
        RoleJpaEntity role = requireConfigurable(roleCode);
        String name = normalizedName(command.name());
        assertUniqueName(name, role.getId());
        Set<PermissionJpaEntity> granted = resolvePermissions(command.permissionCodes());
        role.update(name, normalizeDescription(command.description()), granted, clock.instant());
        RoleJpaEntity saved = roles.saveAndFlush(role);
        revokeAssignedSessions(saved.getId());
        record(actor, "ROLE_UPDATED", "Se actualizaron el rol y sus permisos.", saved,
                Map.of("permissionCount", granted.size()));
        return detail(saved);
    }

    @Transactional
    public RoleDetail cloneRole(String sourceRoleCode, CloneCommand command, Actor actor) {
        RoleJpaEntity source = requireConfigurable(sourceRoleCode);
        String name = normalizedName(command.name());
        assertUniqueName(name, null);
        Instant now = clock.instant();
        Set<PermissionJpaEntity> granted = resolvePermissions(source.getPermissions().stream()
                .map(PermissionJpaEntity::getCode).toList());
        RoleJpaEntity copy = RoleJpaEntity.custom(
                "CUSTOM_" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT),
                name, normalizeDescription(command.description()), granted, now);
        copy = roles.saveAndFlush(copy);
        record(actor, "ROLE_CLONED", "Se clonó un rol con todos sus permisos.", copy,
                Map.of("sourceRoleCode", source.getCode(), "permissionCount", copy.getPermissions().size()));
        return detail(copy);
    }

    @Transactional
    public RoleDetail changeStatus(String roleCode, String requestedStatus, Actor actor) {
        RoleJpaEntity role = requireConfigurable(roleCode);
        String status = normalizeMutableStatus(requestedStatus);
        if (status.equals(role.getStatus())) {
            return detail(role);
        }
        role.changeStatus(status, clock.instant());
        RoleJpaEntity saved = roles.saveAndFlush(role);
        revokeAssignedSessions(saved.getId());
        record(actor, "ACTIVE".equals(status) ? "ROLE_ACTIVATED" : "ROLE_DEACTIVATED",
                "ACTIVE".equals(status) ? "Se activó un rol." : "Se inactivó un rol.", saved, Map.of());
        return detail(saved);
    }

    @Transactional
    public void delete(String roleCode, Actor actor) {
        RoleJpaEntity role = requireConfigurable(roleCode);
        long assignedUsers = users.countAssignedToRole(role.getId());
        if (assignedUsers > 0) {
            throw new BusinessException("ROLE_HAS_USERS",
                    "No es posible eliminar este rol porque tiene usuarios asignados. Reasigna primero a los usuarios a otro rol activo.");
        }
        String code = role.getCode();
        String name = role.getName();
        role.getPermissions().clear();
        roles.saveAndFlush(role);
        roles.delete(role);
        roles.flush();
        audit.record(actor.userId(), "ROLE_DELETED", "ROLE_MANAGEMENT",
                "Se eliminó definitivamente un rol y su configuración de permisos.", actor.ipAddress(),
                actor.userAgent(), Map.of("roleCode", code, "roleName", name), clock.instant());
    }

    private RoleSummary summary(RoleJpaEntity role) {
        int configurablePermissionCount = role.isProtectedAdministrator()
                ? 0 : (int) role.getPermissions().stream()
                        .filter(this::isConfigurableOrganizationalPermission).count();
        return new RoleSummary(role.getCode(), role.getName(), role.getStatus(), role.isProtectedAdministrator(),
                users.countAssignedToRole(role.getId()), configurablePermissionCount, role.getUpdatedAt());
    }

    private RoleDetail detail(RoleJpaEntity role) {
        Set<String> permissionCodes = role.isProtectedAdministrator()
                ? permissions.findAll().stream()
                        .map(PermissionJpaEntity::getCode)
                        .filter(code -> !NON_APPLICABLE_PERMISSIONS.contains(code))
                        .collect(Collectors.toCollection(LinkedHashSet::new))
                : role.getPermissions().stream()
                        .filter(permission -> BASE_PERMISSIONS.contains(permission.getCode())
                                || isOrganizationalPermission(permission))
                        .map(PermissionJpaEntity::getCode)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        return new RoleDetail(role.getCode(), role.getName(), role.getDescription(), role.getStatus(),
                role.isProtectedAdministrator(), users.countAssignedToRole(role.getId()), Set.copyOf(permissionCodes),
                role.getCreatedAt(), role.getUpdatedAt(), role.getVersion());
    }

    private Set<PermissionJpaEntity> resolvePermissions(Collection<String> requestedCodes) {
        Map<String, PermissionJpaEntity> available = permissions.findAll().stream()
                .collect(Collectors.toMap(PermissionJpaEntity::getCode, Function.identity()));
        Set<String> normalized = requestedCodes == null ? new LinkedHashSet<>() : requestedCodes.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> unknown = normalized.stream().filter(code -> !available.containsKey(code))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!unknown.isEmpty()) {
            throw new BusinessException("ROLE_PERMISSION_INVALID",
                    "La configuración contiene permisos que no existen: " + String.join(", ", unknown));
        }

        Set<String> requested = normalized.stream()
                .filter(code -> isOrganizationalPermission(available.get(code)))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        BASE_PERMISSIONS.stream().filter(available::containsKey).forEach(requested::add);

        // La consulta del módulo debe estar seleccionada de forma explícita. Cuando se
        // retira Ver, las acciones dependientes se eliminan en backend en lugar de volver
        // a agregar el permiso de consulta. Esto impide que una selección anterior o un
        // payload manipulado reactive el módulo después de guardar.
        requested.removeIf(code -> {
            String viewPermission = viewPermissionFor(code);
            return !BASE_PERMISSIONS.contains(code)
                    && viewPermission != null
                    && !code.equals(viewPermission)
                    && !BASE_PERMISSIONS.contains(viewPermission)
                    && !requested.contains(viewPermission);
        });

        return requested.stream().map(available::get)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean isOrganizationalPermission(PermissionJpaEntity permission) {
        return permission != null
                && !NON_APPLICABLE_PERMISSIONS.contains(permission.getCode())
                && !ADMINISTRATOR_EXCLUSIVE_PERMISSIONS.contains(permission.getCode())
                && !"USER".equals(permission.getModuleCode());
    }

    private boolean isConfigurableOrganizationalPermission(PermissionJpaEntity permission) {
        return isOrganizationalPermission(permission) && !BASE_PERMISSIONS.contains(permission.getCode());
    }

    private String viewPermissionFor(String code) {
        if (code.startsWith("TALENT_")) return "TALENT_VIEW";
        if (code.startsWith("PROFILE_")) return "PROFILE_VIEW";
        if (code.startsWith("STUDENT_CERTIFICATION_")) return "STUDENT_VIEW";
        if (code.startsWith("STUDENT_")) return "STUDENT_VIEW";
        if (code.startsWith("ORGANIZATION_")) return "ORGANIZATION_VIEW";
        if (code.startsWith("USER_")) return "USER_VIEW";
        if (code.startsWith("FORM_")) return "FORM_VIEW";
        if (code.startsWith("COLLECTION_")) return "COLLECTION_VIEW";
        if (code.startsWith("QUESTION_") || code.startsWith("GLOBAL_CONTENT_")) return "QUESTION_VIEW";
        if (code.startsWith("CATALOG_")) return "CATALOG_VIEW";
        return null;
    }

    private PermissionDescriptor descriptor(PermissionJpaEntity permission) {
        return new PermissionDescriptor(permission.getCode(), permission.getName(), permission.getDescription(),
                actionType(permission.getCode()), isViewPermission(permission.getCode()),
                BASE_PERMISSIONS.contains(permission.getCode()));
    }

    private String actionType(String code) {
        if (isViewPermission(code)) return "VIEW";
        if (code.endsWith("_CREATE")) return "CREATE";
        if (code.endsWith("_UPDATE")) return "UPDATE";
        if (code.endsWith("_DELETE")) return "DELETE";
        if (code.endsWith("_STATUS_CHANGE") || code.endsWith("_ARCHIVE")) return "STATUS";
        return "SPECIAL";
    }

    private boolean isViewPermission(String code) {
        return Set.of("DASHBOARD_VIEW", "PROFILE_VIEW", "USER_VIEW", "ORGANIZATION_VIEW", "STUDENT_VIEW",
                "TALENT_VIEW", "CATALOG_VIEW", "QUESTION_VIEW", "COLLECTION_VIEW", "FORM_VIEW").contains(code);
    }

    private String logicalModuleCode(PermissionJpaEntity permission) {
        String code = permission.getCode();
        if (code.startsWith("GLOBAL_CONTENT_")) return "QUESTION_BANK";
        if (code.startsWith("COLLECTION_")) return "COLLECTIONS";
        if (code.startsWith("FORM_")) return "FORMS";
        if (code.startsWith("TALENT_")) return "TALENT_BANK";
        if (code.startsWith("STUDENT_CERTIFICATION_")) return "STUDENTS";
        if (code.startsWith("STUDENT_")) return "STUDENTS";
        return permission.getModuleCode();
    }

    private String moduleName(String code) {
        return switch (code) {
            case "COLLECTIONS" -> "Colecciones";
            case "CERTIFICATIONS" -> "Certificaciones";
            default -> MODULE_NAMES.getOrDefault(code, humanize(code));
        };
    }

    private int moduleOrder(String code) {
        return switch (code) {
            case "DASHBOARD" -> 10;
            case "PROFILE" -> 20;
            case "ORGANIZATIONS" -> 30;
            case "USER_MANAGEMENT" -> 40;
            case "STUDENTS" -> 50;
            case "TALENT_BANK" -> 60;
            case "CERTIFICATIONS" -> 70;
            case "CATALOGS" -> 80;
            case "QUESTION_BANK" -> 90;
            case "COLLECTIONS" -> 100;
            case "FORMS" -> 110;
            case "GLOBAL_CONTENT" -> 120;
            default -> 500;
        };
    }

    private int permissionOrder(PermissionJpaEntity permission) {
        return switch (actionType(permission.getCode())) {
            case "VIEW" -> 10;
            case "UPDATE" -> 20;
            case "CREATE" -> 30;
            case "DELETE" -> 40;
            case "STATUS" -> 50;
            default -> 100;
        };
    }

    private String humanize(String code) {
        String value = code == null ? "Módulo" : code.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private RoleJpaEntity requireRole(String roleCode) {
        String normalized = roleCode == null ? "" : roleCode.trim();
        return roles.findByCodeIgnoreCase(normalized)
                .orElseThrow(() -> new BusinessException("ROLE_NOT_FOUND", "El rol solicitado no existe."));
    }

    private RoleJpaEntity requireConfigurable(String roleCode) {
        RoleJpaEntity role = requireRole(roleCode);
        if (role.isProtectedAdministrator() || STUDENT_PORTAL_ROLE.equals(role.getCode())) {
            throw new BusinessException("ROLE_PROTECTED",
                    role.isProtectedAdministrator()
                            ? "El rol Administrador está protegido y siempre conserva acceso completo."
                            : "El rol técnico del portal de colaboradores no es configurable.");
        }
        return role;
    }

    private void assertUniqueName(String name, Long excludedId) {
        if (roles.countByNormalizedName(name, excludedId) > 0) {
            throw new BusinessException("ROLE_NAME_EXISTS",
                    "Ya existe un rol registrado con ese nombre.", Map.of("name", "El nombre del rol ya está registrado."));
        }
    }

    private String normalizedName(String value) {
        String normalized = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        if (normalized.isBlank()) {
            throw new BusinessException("ROLE_NAME_REQUIRED", "El nombre del rol es obligatorio.",
                    Map.of("name", "El nombre del rol es obligatorio."));
        }
        if (normalized.length() > 100) {
            throw new BusinessException("ROLE_NAME_TOO_LONG", "El nombre del rol no puede superar 100 caracteres.",
                    Map.of("name", "El nombre del rol no puede superar 100 caracteres."));
        }
        return normalized;
    }

    private String normalizeDescription(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > 500) {
            throw new BusinessException("ROLE_DESCRIPTION_TOO_LONG",
                    "La descripción del rol no puede superar 500 caracteres.",
                    Map.of("description", "La descripción no puede superar 500 caracteres."));
        }
        return normalized;
    }

    private String normalizeCatalogScope(String value) {
        String normalized = value == null || value.isBlank()
                ? "ORGANIZATIONAL" : value.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("ORGANIZATIONAL", "ADMINISTRATOR").contains(normalized)) {
            throw new BusinessException("ROLE_SCOPE_INVALID", "El alcance de permisos solicitado no es válido.");
        }
        return normalized;
    }

    private String normalizeStatusFilter(String value) {
        String normalized = value == null || value.isBlank() ? "ALL" : value.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("ALL", "ACTIVE", "INACTIVE").contains(normalized)) {
            throw new BusinessException("ROLE_STATUS_INVALID", "El filtro de estado no es válido.");
        }
        return normalized;
    }

    private String normalizeMutableStatus(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("ACTIVE", "INACTIVE").contains(normalized)) {
            throw new BusinessException("ROLE_STATUS_INVALID", "El estado solicitado no es válido.");
        }
        return normalized;
    }

    private void revokeAssignedSessions(Long roleId) {
        List<Long> userIds = users.findUserIdsAssignedToRole(roleId);
        if (userIds.isEmpty()) return;
        String placeholders = userIds.stream().map(id -> "?").collect(Collectors.joining(","));
        jdbc.update("UPDATE AUTH_SESSION SET STATUS = 'REVOKED', REVOKED_AT = ? WHERE STATUS = 'ACTIVE' AND USER_ID IN ("
                + placeholders + ")", preparedStatement -> {
                    int index = 1;
                    preparedStatement.setTimestamp(index++, java.sql.Timestamp.from(clock.instant()));
                    for (Long userId : userIds) preparedStatement.setLong(index++, userId);
                });
    }

    private void record(Actor actor, String type, String description, RoleJpaEntity role,
            Map<String, Object> additional) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("roleCode", role.getCode());
        values.put("roleName", role.getName());
        values.put("status", role.getStatus());
        values.putAll(additional);
        audit.record(actor.userId(), type, "ROLE_MANAGEMENT", description, actor.ipAddress(), actor.userAgent(),
                values, clock.instant());
    }

    public record RoleSummary(String code, String name, String status, boolean protectedRole,
            long assignedUsers, int permissionCount, Instant updatedAt) {
    }

    public record RoleDetail(String code, String name, String description, String status, boolean protectedRole,
            long assignedUsers, Set<String> permissionCodes, Instant createdAt, Instant updatedAt, long version) {
    }

    public record PermissionCatalog(List<PermissionModule> modules) {
    }

    public record PermissionModule(String code, String name, List<PermissionDescriptor> permissions) {
    }

    public record PermissionDescriptor(String code, String name, String description, String actionType,
            boolean viewPermission, boolean required) {
    }

    public record UpsertCommand(String name, String description, Collection<String> permissionCodes) {
    }

    public record CloneCommand(String name, String description) {
    }

    public record Actor(Long userId, String ipAddress, String userAgent) {
    }
}
