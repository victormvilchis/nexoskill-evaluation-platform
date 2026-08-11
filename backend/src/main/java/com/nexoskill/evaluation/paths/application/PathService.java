package com.nexoskill.evaluation.paths.application;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.authentication.infrastructure.security.AuthenticatedUser;
import com.nexoskill.evaluation.organizations.application.TenantContextResolver;
import com.nexoskill.evaluation.organizations.domain.model.TenantContext;
import com.nexoskill.evaluation.paths.application.PathModels.CollectionOption;
import com.nexoskill.evaluation.paths.application.PathModels.PathCollectionView;
import com.nexoskill.evaluation.paths.application.PathModels.PathCommand;
import com.nexoskill.evaluation.paths.application.PathModels.PathDetail;
import com.nexoskill.evaluation.paths.application.PathModels.PathSummary;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PathService {
    private static final Set<String> VALID_STATUSES = Set.of("DRAFT", "ACTIVE", "INACTIVE", "ARCHIVED");

    private final NamedParameterJdbcTemplate jdbc;
    private final TenantContextResolver tenants;
    private final HttpServletRequest request;
    private final AuditLogPort audit;
    private final Clock clock;

    public PathService(NamedParameterJdbcTemplate jdbc, TenantContextResolver tenants,
            HttpServletRequest request, AuditLogPort audit, Clock clock) {
        this.jdbc = jdbc;
        this.tenants = tenants;
        this.request = request;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<PathSummary> list(String query, String status, Pageable pageable) {
        TenantContext tenant = tenants.resolve(request);
        String normalizedQuery = blankToNull(query);
        String normalizedStatus = normalizeOptionalStatus(status);
        MapSqlParameterSource params = visibilityParams(tenant)
                .addValue("query", normalizedQuery == null ? null : "%" + normalizedQuery.toUpperCase(Locale.ROOT) + "%")
                .addValue("status", normalizedStatus)
                .addValue("offset", pageable.getOffset())
                .addValue("size", pageable.getPageSize());
        String where = visibleWhere() + " AND (:status IS NULL OR path_value.STATUS=:status) "
                + "AND (:query IS NULL OR UPPER(path_value.PATH_NAME) LIKE :query OR UPPER(path_value.PATH_CODE) LIKE :query "
                + "OR UPPER(NVL(path_value.DESCRIPTION,'')) LIKE :query) ";
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM LEARNING_PATH path_value " + where, params, Long.class);
        String orderBy = orderBy(pageable);
        List<PathSummary> content = jdbc.query("""
                SELECT path_value.PUBLIC_ID, path_value.PATH_CODE, path_value.PATH_NAME, path_value.DESCRIPTION,
                       path_value.STATUS, path_value.CONTENT_SCOPE, organization_value.PUBLIC_ID ORGANIZATION_PUBLIC_ID,
                       organization_value.ORGANIZATION_NAME,
                       (SELECT COUNT(*) FROM LEARNING_PATH_COLLECTION relation_value
                         WHERE relation_value.PATH_ID=path_value.PATH_ID) COLLECTION_COUNT,
                       (SELECT COUNT(DISTINCT level_value.FORM_ID)
                          FROM LEARNING_PATH_COLLECTION relation_value
                          JOIN LEARNING_COLLECTION_LEVEL level_value ON level_value.COLLECTION_ID=relation_value.COLLECTION_ID
                         WHERE relation_value.PATH_ID=path_value.PATH_ID) FORM_COUNT,
                       path_value.UPDATED_AT, path_value.VERSION_NO
                  FROM LEARNING_PATH path_value
                  JOIN ORGANIZATION organization_value ON organization_value.ORGANIZATION_ID=path_value.OWNER_ORGANIZATION_ID
                """ + where + orderBy + " OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY", params,
                (rs, rowNum) -> new PathSummary(rs.getString("PUBLIC_ID"), rs.getString("PATH_CODE"),
                        rs.getString("PATH_NAME"), rs.getString("DESCRIPTION"), rs.getString("STATUS"),
                        rs.getString("CONTENT_SCOPE"), rs.getString("ORGANIZATION_PUBLIC_ID"),
                        rs.getString("ORGANIZATION_NAME"), rs.getInt("COLLECTION_COUNT"), rs.getInt("FORM_COUNT"),
                        rs.getObject("UPDATED_AT", OffsetDateTime.class), rs.getLong("VERSION_NO")));
        long safeTotal = total == null ? 0 : total;
        return new PageImpl<>(content, pageable, safeTotal);
    }

    @Transactional(readOnly = true)
    public PathDetail get(String publicId) {
        TenantContext tenant = tenants.resolve(request);
        PathRow path = requireVisiblePath(canonicalUuid(publicId), tenant, false);
        return detail(path);
    }

    @Transactional(readOnly = true)
    public List<CollectionOption> collectionOptions(String query) {
        TenantContext tenant = tenants.resolve(request);
        String normalizedQuery = blankToNull(query);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("global", tenant.globalScope() ? 1 : 0)
                .addValue("organizationId", tenant.organizationId())
                .addValue("query", normalizedQuery == null ? null : "%" + normalizedQuery.toUpperCase(Locale.ROOT) + "%")
                .addValue("now", OffsetDateTime.now(clock));
        return jdbc.query("""
                SELECT collection_value.PUBLIC_ID, collection_value.COLLECTION_NAME, collection_value.DESCRIPTION,
                       collection_value.STATUS, collection_value.CONTENT_SCOPE,
                       organization_value.PUBLIC_ID ORGANIZATION_PUBLIC_ID, organization_value.ORGANIZATION_NAME,
                       COUNT(level_value.LEVEL_ID) FORM_COUNT
                  FROM LEARNING_COLLECTION collection_value
                  JOIN ORGANIZATION organization_value ON organization_value.ORGANIZATION_ID=collection_value.OWNER_ORGANIZATION_ID
                  LEFT JOIN LEARNING_COLLECTION_LEVEL level_value ON level_value.COLLECTION_ID=collection_value.COLLECTION_ID
                 WHERE collection_value.STATUS='ACTIVE'
                   AND (
                        (:global=1 AND collection_value.CONTENT_SCOPE='GLOBAL')
                        OR (:global=0 AND (
                            (collection_value.CONTENT_SCOPE='ORGANIZATION' AND collection_value.OWNER_ORGANIZATION_ID=:organizationId)
                            OR (collection_value.CONTENT_SCOPE='GLOBAL' AND (
                                  EXISTS (SELECT 1 FROM ORGANIZATION org_value
                                           WHERE org_value.ORGANIZATION_ID=:organizationId AND org_value.CONTENT_MODE='GLOBAL_CATALOG')
                                  OR EXISTS (
                                        SELECT 1 FROM ORGANIZATION_GLOBAL_CONTENT_GRANT grant_value
                                         WHERE grant_value.ORGANIZATION_ID=:organizationId
                                           AND grant_value.CONTENT_TYPE='COLLECTION'
                                           AND grant_value.GLOBAL_CONTENT_ID=collection_value.COLLECTION_ID
                                           AND grant_value.STATUS='ACTIVE'
                                           AND grant_value.DISTRIBUTION_MODE='GLOBAL_REFERENCE'
                                           AND (grant_value.AVAILABLE_FROM IS NULL OR grant_value.AVAILABLE_FROM<=SYSTIMESTAMP)
                                           AND (grant_value.EXPIRES_AT IS NULL OR grant_value.EXPIRES_AT>SYSTIMESTAMP)
                                  )
                            ))
                        ))
                   )
                   AND (:query IS NULL OR UPPER(collection_value.COLLECTION_NAME) LIKE :query
                        OR UPPER(collection_value.COLLECTION_CODE) LIKE :query
                        OR UPPER(NVL(collection_value.DESCRIPTION,'')) LIKE :query)
                 GROUP BY collection_value.PUBLIC_ID, collection_value.COLLECTION_NAME, collection_value.DESCRIPTION,
                          collection_value.STATUS, collection_value.CONTENT_SCOPE,
                          organization_value.PUBLIC_ID, organization_value.ORGANIZATION_NAME
                 ORDER BY UPPER(collection_value.COLLECTION_NAME), collection_value.PUBLIC_ID
                 FETCH FIRST 100 ROWS ONLY
                """, params, (rs, rowNum) -> new CollectionOption(rs.getString("PUBLIC_ID"),
                        rs.getString("COLLECTION_NAME"), rs.getString("DESCRIPTION"), rs.getString("STATUS"),
                        rs.getString("CONTENT_SCOPE"), rs.getString("ORGANIZATION_PUBLIC_ID"),
                        rs.getString("ORGANIZATION_NAME"), rs.getInt("FORM_COUNT")));
    }

    @Transactional
    public PathDetail create(PathCommand command, AuthenticatedUser actor) {
        requireActor(actor);
        validateCommand(command);
        TenantContext tenant = tenants.resolve(request);
        if (!tenant.hasOrganization()) throw new BusinessException("TENANT_NOT_RESOLVED", "No fue posible determinar el alcance del Path.");
        String scope = tenant.globalScope() ? "GLOBAL" : "ORGANIZATION";
        Long ownerOrganizationId = tenant.organizationId();
        List<CollectionRow> collections = resolveCollections(command.collectionPublicIds(), scope, ownerOrganizationId, tenant);
        ensureUniqueName(scope, ownerOrganizationId, command.name(), null);
        String publicId = UUID.randomUUID().toString();
        String code = uniqueCode(scope, ownerOrganizationId, command.name());
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("publicId", publicId).addValue("code", code).addValue("name", command.name().trim())
                .addValue("description", trimToNull(command.description())).addValue("scope", scope)
                .addValue("organizationId", ownerOrganizationId).addValue("actorId", actor.internalId());
        jdbc.update("""
                INSERT INTO LEARNING_PATH
                    (PUBLIC_ID, PATH_CODE, PATH_NAME, DESCRIPTION, STATUS, CONTENT_SCOPE, OWNER_ORGANIZATION_ID,
                     CREATED_BY, UPDATED_BY, CREATED_AT, UPDATED_AT, VERSION_NO)
                VALUES (:publicId, :code, :name, :description, 'DRAFT', :scope, :organizationId,
                        :actorId, :actorId, SYSTIMESTAMP, SYSTIMESTAMP, 0)
                """, params);
        Long pathId = jdbc.queryForObject("SELECT PATH_ID FROM LEARNING_PATH WHERE PUBLIC_ID=:publicId", params, Long.class);
        replaceCollections(pathId, collections);
        record(actor, "PATH_CREATED", "Se creó el Path " + command.name().trim() + ".", publicId,
                Map.of("pathPublicId", publicId, "collectionCount", collections.size(), "scope", scope));
        return detail(requireVisiblePath(publicId, tenant, false));
    }

    @Transactional
    public PathDetail update(String publicId, PathCommand command, AuthenticatedUser actor) {
        requireActor(actor);
        validateCommand(command);
        TenantContext tenant = tenants.resolve(request);
        PathRow path = requireVisiblePath(canonicalUuid(publicId), tenant, true);
        assertEditable(path, tenant);
        if ("ARCHIVED".equals(path.status())) throw new BusinessException("PATH_ARCHIVED", "Un Path archivado no puede modificarse.");
        if (command.version() != null && !Objects.equals(command.version(), path.version())) {
            throw new BusinessException("PATH_CONCURRENT_MODIFICATION", "El Path fue modificado por otra sesión. Recarga e intenta nuevamente.");
        }
        List<CollectionRow> collections = resolveCollections(command.collectionPublicIds(), path.scope(), path.ownerOrganizationId(), tenant);
        if ("ACTIVE".equals(path.status())) validateActivation(collections);
        ensureUniqueName(path.scope(), path.ownerOrganizationId(), command.name(), path.id());
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("id", path.id())
                .addValue("name", command.name().trim()).addValue("description", trimToNull(command.description()))
                .addValue("actorId", actor.internalId()).addValue("version", path.version());
        int updated = jdbc.update("""
                UPDATE LEARNING_PATH
                   SET PATH_NAME=:name, DESCRIPTION=:description, UPDATED_BY=:actorId,
                       UPDATED_AT=SYSTIMESTAMP, VERSION_NO=VERSION_NO+1
                 WHERE PATH_ID=:id AND VERSION_NO=:version
                """, params);
        if (updated != 1) throw new BusinessException("PATH_CONCURRENT_MODIFICATION", "El Path fue modificado por otra sesión. Recarga e intenta nuevamente.");
        replaceCollections(path.id(), collections);
        record(actor, "PATH_UPDATED", "Se actualizó el Path " + command.name().trim() + ".", path.publicId(),
                Map.of("pathPublicId", path.publicId(), "collectionCount", collections.size()));
        return detail(requireVisiblePath(path.publicId(), tenant, false));
    }

    @Transactional
    public PathDetail changeStatus(String publicId, String requestedStatus, AuthenticatedUser actor) {
        requireActor(actor);
        String status = normalizeRequiredStatus(requestedStatus);
        TenantContext tenant = tenants.resolve(request);
        PathRow path = requireVisiblePath(canonicalUuid(publicId), tenant, true);
        assertEditable(path, tenant);
        if ("ACTIVE".equals(status)) validateActivation(loadCollectionRows(path.id()));
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("id", path.id())
                .addValue("status", status).addValue("actorId", actor.internalId()).addValue("version", path.version());
        int updated = jdbc.update("""
                UPDATE LEARNING_PATH
                   SET STATUS=:status, UPDATED_BY=:actorId, UPDATED_AT=SYSTIMESTAMP, VERSION_NO=VERSION_NO+1
                 WHERE PATH_ID=:id AND VERSION_NO=:version
                """, params);
        if (updated != 1) throw new BusinessException("PATH_CONCURRENT_MODIFICATION", "El Path fue modificado por otra sesión. Recarga e intenta nuevamente.");
        record(actor, "PATH_STATUS_CHANGED", "El Path cambió a estado " + statusLabel(status) + ".", path.publicId(),
                Map.of("pathPublicId", path.publicId(), "status", status));
        return detail(requireVisiblePath(path.publicId(), tenant, false));
    }

    private PathDetail detail(PathRow path) {
        List<PathCollectionView> collections = jdbc.query("""
                SELECT relation_value.COLLECTION_ORDER, collection_value.PUBLIC_ID, collection_value.COLLECTION_NAME,
                       collection_value.DESCRIPTION, collection_value.STATUS, collection_value.CONTENT_SCOPE,
                       organization_value.PUBLIC_ID ORGANIZATION_PUBLIC_ID, organization_value.ORGANIZATION_NAME,
                       COUNT(level_value.LEVEL_ID) FORM_COUNT
                  FROM LEARNING_PATH_COLLECTION relation_value
                  JOIN LEARNING_COLLECTION collection_value ON collection_value.COLLECTION_ID=relation_value.COLLECTION_ID
                  JOIN ORGANIZATION organization_value ON organization_value.ORGANIZATION_ID=collection_value.OWNER_ORGANIZATION_ID
                  LEFT JOIN LEARNING_COLLECTION_LEVEL level_value ON level_value.COLLECTION_ID=collection_value.COLLECTION_ID
                 WHERE relation_value.PATH_ID=:pathId
                 GROUP BY relation_value.COLLECTION_ORDER, collection_value.PUBLIC_ID, collection_value.COLLECTION_NAME,
                          collection_value.DESCRIPTION, collection_value.STATUS, collection_value.CONTENT_SCOPE,
                          organization_value.PUBLIC_ID, organization_value.ORGANIZATION_NAME
                 ORDER BY relation_value.COLLECTION_ORDER
                """, Map.of("pathId", path.id()), (rs, rowNum) -> new PathCollectionView(rs.getInt("COLLECTION_ORDER"),
                        rs.getString("PUBLIC_ID"), rs.getString("COLLECTION_NAME"), rs.getString("DESCRIPTION"),
                        rs.getString("STATUS"), rs.getString("CONTENT_SCOPE"), rs.getString("ORGANIZATION_PUBLIC_ID"),
                        rs.getString("ORGANIZATION_NAME"), rs.getInt("FORM_COUNT")));
        return new PathDetail(path.publicId(), path.code(), path.name(), path.description(), path.status(), path.scope(),
                path.organizationPublicId(), path.organizationName(), collections, path.createdAt(), path.updatedAt(), path.version());
    }

    private PathRow requireVisiblePath(String publicId, TenantContext tenant, boolean forUpdate) {
        MapSqlParameterSource params = visibilityParams(tenant).addValue("publicId", publicId);
        String suffix = forUpdate ? " FOR UPDATE" : "";
        List<PathRow> rows = jdbc.query("""
                SELECT path_value.PATH_ID, path_value.PUBLIC_ID, path_value.PATH_CODE, path_value.PATH_NAME,
                       path_value.DESCRIPTION, path_value.STATUS, path_value.CONTENT_SCOPE, path_value.OWNER_ORGANIZATION_ID,
                       organization_value.PUBLIC_ID ORGANIZATION_PUBLIC_ID, organization_value.ORGANIZATION_NAME,
                       path_value.CREATED_AT, path_value.UPDATED_AT, path_value.VERSION_NO
                  FROM LEARNING_PATH path_value
                  JOIN ORGANIZATION organization_value ON organization_value.ORGANIZATION_ID=path_value.OWNER_ORGANIZATION_ID
                """ + visibleWhere() + " AND path_value.PUBLIC_ID=:publicId" + suffix, params,
                (rs, rowNum) -> new PathRow(rs.getLong("PATH_ID"), rs.getString("PUBLIC_ID"), rs.getString("PATH_CODE"),
                        rs.getString("PATH_NAME"), rs.getString("DESCRIPTION"), rs.getString("STATUS"),
                        rs.getString("CONTENT_SCOPE"), rs.getLong("OWNER_ORGANIZATION_ID"),
                        rs.getString("ORGANIZATION_PUBLIC_ID"), rs.getString("ORGANIZATION_NAME"),
                        rs.getObject("CREATED_AT", OffsetDateTime.class), rs.getObject("UPDATED_AT", OffsetDateTime.class),
                        rs.getLong("VERSION_NO")));
        if (rows.isEmpty()) throw new BusinessException("PATH_NOT_FOUND", "El Path solicitado no existe o está fuera de tu alcance.");
        return rows.getFirst();
    }

    private String visibleWhere() {
        return " WHERE (:global=1 OR path_value.CONTENT_SCOPE='GLOBAL' "
                + "OR (path_value.CONTENT_SCOPE='ORGANIZATION' AND path_value.OWNER_ORGANIZATION_ID=:organizationId)) ";
    }

    private MapSqlParameterSource visibilityParams(TenantContext tenant) {
        return new MapSqlParameterSource().addValue("global", tenant.globalScope() ? 1 : 0)
                .addValue("organizationId", tenant.organizationId());
    }

    private void assertEditable(PathRow path, TenantContext tenant) {
        if (tenant.globalScope() && tenant.globalAdministrator()) return;
        if (!tenant.hasOrganization() || !Objects.equals(path.ownerOrganizationId(), tenant.organizationId())
                || "GLOBAL".equals(path.scope())) {
            throw new BusinessException("PATH_OUT_OF_SCOPE", "No puedes modificar un Path fuera de tu organización.");
        }
    }

    private List<CollectionRow> resolveCollections(List<String> publicIds, String pathScope, Long ownerOrganizationId,
            TenantContext tenant) {
        List<String> ids = publicIds == null ? List.of() : publicIds.stream().map(this::canonicalUuid).toList();
        LinkedHashSet<String> unique = new LinkedHashSet<>(ids);
        if (unique.size() != ids.size()) throw new BusinessException("PATH_COLLECTION_DUPLICATE", "Una Colección no puede aparecer dos veces dentro del mismo Path.");
        List<CollectionRow> result = new ArrayList<>();
        for (String publicId : unique) {
            List<CollectionRow> rows = jdbc.query("""
                    SELECT collection_value.COLLECTION_ID, collection_value.PUBLIC_ID, collection_value.COLLECTION_NAME,
                           collection_value.STATUS, collection_value.CONTENT_SCOPE, collection_value.OWNER_ORGANIZATION_ID
                      FROM LEARNING_COLLECTION collection_value
                     WHERE collection_value.PUBLIC_ID=:publicId
                    """, Map.of("publicId", publicId), (rs, rowNum) -> new CollectionRow(rs.getLong("COLLECTION_ID"),
                            rs.getString("PUBLIC_ID"), rs.getString("COLLECTION_NAME"), rs.getString("STATUS"),
                            rs.getString("CONTENT_SCOPE"), rs.getLong("OWNER_ORGANIZATION_ID")));
            if (rows.isEmpty()) throw new BusinessException("PATH_COLLECTION_NOT_FOUND", "Una de las Colecciones seleccionadas ya no existe.");
            CollectionRow collection = rows.getFirst();
            assertCollectionScope(collection, pathScope, ownerOrganizationId, tenant);
            result.add(collection);
        }
        return List.copyOf(result);
    }

    private List<CollectionRow> loadCollectionRows(Long pathId) {
        return jdbc.query("""
                SELECT collection_value.COLLECTION_ID, collection_value.PUBLIC_ID, collection_value.COLLECTION_NAME,
                       collection_value.STATUS, collection_value.CONTENT_SCOPE, collection_value.OWNER_ORGANIZATION_ID
                  FROM LEARNING_PATH_COLLECTION relation_value
                  JOIN LEARNING_COLLECTION collection_value ON collection_value.COLLECTION_ID=relation_value.COLLECTION_ID
                 WHERE relation_value.PATH_ID=:pathId
                 ORDER BY relation_value.COLLECTION_ORDER
                """, Map.of("pathId", pathId), (rs, rowNum) -> new CollectionRow(rs.getLong("COLLECTION_ID"),
                        rs.getString("PUBLIC_ID"), rs.getString("COLLECTION_NAME"), rs.getString("STATUS"),
                        rs.getString("CONTENT_SCOPE"), rs.getLong("OWNER_ORGANIZATION_ID")));
    }

    private void assertCollectionScope(CollectionRow collection, String pathScope, Long ownerOrganizationId, TenantContext tenant) {
        if ("GLOBAL".equals(pathScope)) {
            if (!"GLOBAL".equals(collection.scope())) {
                throw new BusinessException("PATH_COLLECTION_SCOPE_INVALID", "Un Path global únicamente puede utilizar Colecciones globales.");
            }
            return;
        }
        if ("ORGANIZATION".equals(collection.scope()) && Objects.equals(collection.ownerOrganizationId(), ownerOrganizationId)) return;
        if ("GLOBAL".equals(collection.scope()) && globalCollectionAvailable(collection.id(), ownerOrganizationId)) return;
        throw new BusinessException("PATH_COLLECTION_SCOPE_INVALID", "La Colección " + collection.name() + " pertenece a otro alcance y no puede utilizarse en este Path.");
    }

    private boolean globalCollectionAvailable(Long collectionId, Long organizationId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM ORGANIZATION organization_value
                 WHERE organization_value.ORGANIZATION_ID=:organizationId
                   AND (
                        organization_value.CONTENT_MODE='GLOBAL_CATALOG'
                        OR EXISTS (
                            SELECT 1 FROM ORGANIZATION_GLOBAL_CONTENT_GRANT grant_value
                             WHERE grant_value.ORGANIZATION_ID=:organizationId
                               AND grant_value.CONTENT_TYPE='COLLECTION'
                               AND grant_value.GLOBAL_CONTENT_ID=:collectionId
                               AND grant_value.STATUS='ACTIVE'
                               AND grant_value.DISTRIBUTION_MODE='GLOBAL_REFERENCE'
                               AND (grant_value.AVAILABLE_FROM IS NULL OR grant_value.AVAILABLE_FROM<=SYSTIMESTAMP)
                               AND (grant_value.EXPIRES_AT IS NULL OR grant_value.EXPIRES_AT>SYSTIMESTAMP)
                        )
                   )
                """, new MapSqlParameterSource().addValue("organizationId", organizationId).addValue("collectionId", collectionId), Integer.class);
        return count != null && count > 0;
    }

    private void validateActivation(List<CollectionRow> collections) {
        CollectionRow unavailable = collections.stream().filter(item -> !"ACTIVE".equals(item.status())).findFirst().orElse(null);
        if (unavailable != null) {
            throw new BusinessException("PATH_COLLECTION_INACTIVE", "La Colección " + unavailable.name() + " debe estar activa antes de activar el Path.");
        }
    }

    private void replaceCollections(Long pathId, List<CollectionRow> collections) {
        jdbc.update("DELETE FROM LEARNING_PATH_COLLECTION WHERE PATH_ID=:pathId", Map.of("pathId", pathId));
        int order = 1;
        for (CollectionRow collection : collections) {
            jdbc.update("""
                    INSERT INTO LEARNING_PATH_COLLECTION (PATH_ID, COLLECTION_ID, COLLECTION_ORDER, CREATED_AT)
                    VALUES (:pathId, :collectionId, :collectionOrder, SYSTIMESTAMP)
                    """, new MapSqlParameterSource().addValue("pathId", pathId).addValue("collectionId", collection.id())
                            .addValue("collectionOrder", order++));
        }
    }

    private void ensureUniqueName(String scope, Long ownerOrganizationId, String name, Long excludeId) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("scope", scope)
                .addValue("organizationId", ownerOrganizationId).addValue("name", name.trim())
                .addValue("excludeId", excludeId);
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM LEARNING_PATH
                 WHERE CONTENT_SCOPE=:scope AND OWNER_ORGANIZATION_ID=:organizationId
                   AND UPPER(TRIM(PATH_NAME))=UPPER(TRIM(:name))
                   AND (:excludeId IS NULL OR PATH_ID<>:excludeId)
                """, params, Integer.class);
        if (count != null && count > 0) throw new BusinessException("PATH_NAME_DUPLICATE", "Ya existe un Path con ese nombre dentro del mismo alcance.");
    }

    private String uniqueCode(String scope, Long ownerOrganizationId, String name) {
        String base = "PATH_" + slug(name);
        if (base.length() > 90) base = base.substring(0, 90);
        String candidate = base;
        int suffix = 2;
        while (codeExists(scope, ownerOrganizationId, candidate)) {
            String extra = "_" + suffix++;
            int maxBase = Math.max(1, 100 - extra.length());
            candidate = (base.length() > maxBase ? base.substring(0, maxBase) : base) + extra;
        }
        return candidate;
    }

    private boolean codeExists(String scope, Long ownerOrganizationId, String code) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM LEARNING_PATH WHERE CONTENT_SCOPE=:scope AND OWNER_ORGANIZATION_ID=:organizationId AND PATH_CODE=:code",
                new MapSqlParameterSource().addValue("scope", scope).addValue("organizationId", ownerOrganizationId).addValue("code", code), Integer.class);
        return count != null && count > 0;
    }

    private String orderBy(Pageable pageable) {
        var order = pageable.getSort().stream().findFirst().orElse(null);
        String property = order == null ? "updatedAt" : order.getProperty();
        String column = switch (property) {
            case "name" -> "path_value.PATH_NAME";
            case "code" -> "path_value.PATH_CODE";
            case "status" -> "path_value.STATUS";
            case "createdAt" -> "path_value.CREATED_AT";
            default -> "path_value.UPDATED_AT";
        };
        String direction = order != null && order.isAscending() ? " ASC" : " DESC";
        return " ORDER BY " + column + direction + ", path_value.PATH_ID DESC";
    }

    private void validateCommand(PathCommand command) {
        if (command == null || command.name() == null || command.name().isBlank()) {
            throw new BusinessException("PATH_NAME_REQUIRED", "El nombre del Path es obligatorio.");
        }
        if (command.name().trim().length() > 200) throw new BusinessException("PATH_NAME_TOO_LONG", "El nombre del Path no puede exceder 200 caracteres.");
        if (command.description() != null && command.description().trim().length() > 2000) {
            throw new BusinessException("PATH_DESCRIPTION_TOO_LONG", "La descripción no puede exceder 2000 caracteres.");
        }
    }

    private String normalizeOptionalStatus(String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) return null;
        return normalizeRequiredStatus(status);
    }

    private String normalizeRequiredStatus(String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!VALID_STATUSES.contains(normalized)) throw new BusinessException("PATH_STATUS_INVALID", "El estado indicado no es válido.");
        return normalized;
    }

    private String statusLabel(String status) {
        return switch (status) {
            case "DRAFT" -> "Borrador";
            case "ACTIVE" -> "Activo";
            case "INACTIVE" -> "Inactivo";
            case "ARCHIVED" -> "Archivado";
            default -> status;
        };
    }

    private String canonicalUuid(String value) {
        if (value == null || value.isBlank()) throw new BusinessException("PATH_ID_REQUIRED", "El identificador es obligatorio.");
        try { return UUID.fromString(value.trim()).toString(); }
        catch (IllegalArgumentException exception) { throw new BusinessException("PATH_ID_INVALID", "El identificador del Path no es válido."); }
    }

    private String slug(String value) {
        String ascii = Normalizer.normalize(value == null ? "PATH" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return ascii.isBlank() ? "NUEVO" : ascii;
    }

    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String trimToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static void requireActor(AuthenticatedUser actor) {
        if (actor == null) throw new BusinessException("AUTHENTICATION_REQUIRED", "Debes iniciar sesión nuevamente.");
    }

    private void record(AuthenticatedUser actor, String type, String description, String publicId, Map<String, Object> data) {
        audit.record(actor.internalId(), type, "PATHS", description, request.getRemoteAddr(), request.getHeader("User-Agent"), data, Instant.now(clock));
    }

    private record PathRow(Long id, String publicId, String code, String name, String description, String status,
            String scope, Long ownerOrganizationId, String organizationPublicId, String organizationName,
            OffsetDateTime createdAt, OffsetDateTime updatedAt, Long version) {}
    private record CollectionRow(Long id, String publicId, String name, String status, String scope, Long ownerOrganizationId) {}
}
