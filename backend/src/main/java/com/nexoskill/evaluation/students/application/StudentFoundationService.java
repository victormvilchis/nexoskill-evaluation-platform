package com.nexoskill.evaluation.students.application;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.organizations.domain.model.OrganizationType;
import com.nexoskill.evaluation.organizations.domain.model.TenantContext;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationJpaEntity;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationRepository;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import com.nexoskill.evaluation.students.domain.StudentEffectiveStatus;
import com.nexoskill.evaluation.students.domain.StudentStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentFoundationService {
    private static final String BASE_SELECT = """
        SELECT s.PUBLIC_ID, s.STUDENT_CODE, s.EMAIL, s.FIRST_NAME, s.LAST_NAME, s.DISPLAY_NAME,
               s.STATUS, s.VALID_FROM, s.EXPIRES_AT, s.PASSWORD_CHANGE_REQUIRED,
               s.TEMP_PASSWORD_EXPIRES_AT, s.LAST_LOGIN_AT, s.ARCHIVED_AT, s.DELETED_AT,
               s.DELETION_REASON, s.CREATED_AT, s.UPDATED_AT, s.VERSION_NO,
               o.PUBLIC_ID ORGANIZATION_PUBLIC_ID, o.ORGANIZATION_CODE, o.ORGANIZATION_NAME,
               o.APPLIES_CERTIFICATIONS,
               profile.PUBLIC_ID PROFILE_PUBLIC_ID, profile.PROFILE_CODE, profile.PROFILE_NAME,
               tech_profile.PUBLIC_ID TECH_PROFILE_PUBLIC_ID,
               tech_profile.PROFILE_CODE TECH_PROFILE_CODE, tech_profile.PROFILE_NAME TECH_PROFILE_NAME,
               technology.PUBLIC_ID TECHNOLOGY_PUBLIC_ID,
               technology.TECHNOLOGY_CODE, technology.TECHNOLOGY_NAME,
               s.CERTIFICATIONS_ENABLED, s.CERTIFICATION_ENROLLMENT_DATE
          FROM STUDENT s
          JOIN ORGANIZATION o ON o.ORGANIZATION_ID = s.ORGANIZATION_ID
          LEFT JOIN CERTIFICATION_PROFILE_CATALOG profile
            ON profile.CERTIFICATION_PROFILE_ID = s.PROFESSIONAL_PROFILE_ID
          LEFT JOIN TECHNOLOGICAL_PROFILE_CATALOG tech_profile
            ON tech_profile.TECHNOLOGICAL_PROFILE_ID = s.TECHNOLOGICAL_PROFILE_ID
          LEFT JOIN QUESTION_TECHNOLOGY technology
            ON technology.TECHNOLOGY_ID = s.TECHNOLOGY_ID
        """;

    private final StudentService studentService;
    private final OrganizationRepository organizationRepository;
    private final NamedParameterJdbcTemplate jdbc;
    private final AuditLogPort audit;
    private final Clock clock;

    public StudentFoundationService(StudentService studentService, OrganizationRepository organizationRepository,
            NamedParameterJdbcTemplate jdbc, AuditLogPort audit, Clock clock) {
        this.studentService = studentService;
        this.organizationRepository = organizationRepository;
        this.jdbc = jdbc;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResult search(TenantContext tenant, SearchCriteria criteria, int page, int size) {
        requireTenant(tenant);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("now", Timestamp.from(clock.instant()))
                .addValue("offset", page * size)
                .addValue("size", size);
        String where = buildWhere(tenant, criteria, params);
        long total = jdbc.queryForObject("""
            SELECT COUNT(*)
              FROM STUDENT s
              JOIN ORGANIZATION o ON o.ORGANIZATION_ID = s.ORGANIZATION_ID
              LEFT JOIN CERTIFICATION_PROFILE_CATALOG profile
                ON profile.CERTIFICATION_PROFILE_ID = s.PROFESSIONAL_PROFILE_ID
              LEFT JOIN TECHNOLOGICAL_PROFILE_CATALOG tech_profile
                ON tech_profile.TECHNOLOGICAL_PROFILE_ID = s.TECHNOLOGICAL_PROFILE_ID
              LEFT JOIN QUESTION_TECHNOLOGY technology
                ON technology.TECHNOLOGY_ID = s.TECHNOLOGY_ID
            """ + where, params, Long.class);
        if (total == 0) return new PageResult(List.of(), page, size, 0, 0);
        String order = allowedOrder(criteria.sort(), criteria.direction());
        List<StudentView> content = jdbc.query(BASE_SELECT + where + " ORDER BY " + order
                + " OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY", params, this::mapStudent);
        int totalPages = (int) Math.ceil((double) total / size);
        return new PageResult(content, page, size, total, totalPages);
    }

    @Transactional(readOnly = true)
    public StudentView get(TenantContext tenant, String publicId) {
        TenantContext effective = effectiveTenantForStudent(tenant, publicId);
        MapSqlParameterSource params = new MapSqlParameterSource("publicId", publicId)
                .addValue("organizationId", effective.organizationId());
        List<StudentView> rows = jdbc.query(BASE_SELECT + " WHERE s.PUBLIC_ID = :publicId AND s.ORGANIZATION_ID = :organizationId",
                params, this::mapStudent);
        if (rows.isEmpty()) throw new BusinessException("STUDENT_NOT_FOUND", "El estudiante no existe.");
        return rows.getFirst();
    }

    @Transactional
    public StudentView create(TenantContext tenant, CreateCommand command, StudentService.Actor actor) {
        TenantContext effective = resolveCreateTenant(tenant, command.organizationPublicId());
        OrganizationJpaEntity organization = organizationRepository.findById(effective.organizationId())
                .orElseThrow(() -> new BusinessException("ORGANIZATION_NOT_FOUND", "La organización no existe."));
        StudentStatus initialStatus = command.status() == null ? StudentStatus.ACTIVE : command.status();
        StudentService.StudentDetail created = studentService.create(effective,
                new StudentService.CreateCommand(command.studentCode(), command.email(), command.firstName(),
                        command.lastName(), command.displayName(), command.temporaryPassword(), initialStatus,
                        command.validFrom(), command.expiresAt()), actor);
        updateFoundation(created.publicId(), organization, command.professionalProfilePublicId(),
                command.technologicalProfilePublicId(), command.technologyPublicId(),
                command.certificationEnrollmentDate(), actor.userId());
        recordFoundationAudit(actor, "STUDENT_FOUNDATION_CREATED", created.publicId(), organization,
                command.professionalProfilePublicId(), command.technologicalProfilePublicId(),
                command.technologyPublicId());
        return get(tenant, created.publicId());
    }

    @Transactional
    public StudentView update(TenantContext tenant, String publicId, UpdateCommand command, StudentService.Actor actor) {
        TenantContext effective = effectiveTenantForStudent(tenant, publicId);
        OrganizationJpaEntity organization = organizationRepository.findById(effective.organizationId())
                .orElseThrow(() -> new BusinessException("ORGANIZATION_NOT_FOUND", "La organización no existe."));
        studentService.update(effective, publicId,
                new StudentService.UpdateCommand(command.email(), command.firstName(), command.lastName(),
                        command.displayName(), command.validFrom(), command.expiresAt(), command.version()), actor);
        updateFoundation(publicId, organization, command.professionalProfilePublicId(),
                command.technologicalProfilePublicId(), command.technologyPublicId(),
                command.certificationEnrollmentDate(), actor.userId());
        recordFoundationAudit(actor, "STUDENT_FOUNDATION_UPDATED", publicId, organization,
                command.professionalProfilePublicId(), command.technologicalProfilePublicId(),
                command.technologyPublicId());
        return get(tenant, publicId);
    }

    @Transactional(readOnly = true)
    public CatalogBundle catalogs() {
        return new CatalogBundle(
                catalog("CERTIFICATION_PROFILE_CATALOG", "PUBLIC_ID", "PROFILE_CODE", "PROFILE_NAME", "SORT_ORDER"),
                catalog("TECHNOLOGICAL_PROFILE_CATALOG", "PUBLIC_ID", "PROFILE_CODE", "PROFILE_NAME", "DISPLAY_ORDER"),
                catalog("QUESTION_TECHNOLOGY", "PUBLIC_ID", "TECHNOLOGY_CODE", "TECHNOLOGY_NAME", "DISPLAY_ORDER"));
    }

    @Transactional(readOnly = true)
    public TenantContext effectiveTenantForStudent(TenantContext tenant, String publicId) {
        requireTenant(tenant);
        if (!tenant.globalAdministrator()) return tenant;
        List<TenantContext> rows = jdbc.query("""
            SELECT o.ORGANIZATION_ID, o.PUBLIC_ID, o.ORGANIZATION_CODE
              FROM STUDENT s
              JOIN ORGANIZATION o ON o.ORGANIZATION_ID = s.ORGANIZATION_ID
             WHERE s.PUBLIC_ID = :publicId AND o.ORGANIZATION_TYPE = 'CUSTOMER'
            """, Map.of("publicId", publicId), (rs, rowNum) -> TenantContext.organization(
                rs.getLong("ORGANIZATION_ID"), rs.getString("PUBLIC_ID"),
                rs.getString("ORGANIZATION_CODE"), true));
        if (rows.isEmpty()) throw new BusinessException("STUDENT_NOT_FOUND", "El estudiante no existe.");
        return rows.getFirst();
    }

    private TenantContext resolveCreateTenant(TenantContext tenant, String organizationPublicId) {
        requireTenant(tenant);
        if (!tenant.globalAdministrator()) {
            validateOperationalOrganization(tenant.organizationId());
            return tenant;
        }
        if (organizationPublicId == null || organizationPublicId.isBlank()) {
            throw new BusinessException("STUDENT_ORGANIZATION_REQUIRED", "Selecciona la organización del estudiante.");
        }
        OrganizationJpaEntity organization = organizationRepository.findByPublicId(organizationPublicId.trim())
                .orElseThrow(() -> new BusinessException("ORGANIZATION_NOT_FOUND", "La organización no existe."));
        validateOperationalOrganization(organization.getId());
        return TenantContext.organization(organization.getId(), organization.getPublicId(), organization.getCode(), true);
    }

    private void validateOperationalOrganization(Long organizationId) {
        OrganizationJpaEntity organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new BusinessException("ORGANIZATION_NOT_FOUND", "La organización no existe."));
        if (organization.getOrganizationType() != OrganizationType.CUSTOMER || organization.isGlobal()) {
            throw new BusinessException("STUDENT_GLOBAL_FORBIDDEN", "Los estudiantes no pueden pertenecer a GLOBAL.");
        }
        if (!organization.isOperational(LocalDate.now(clock))) {
            throw new BusinessException("ORGANIZATION_NOT_OPERATIONAL",
                    "La organización debe estar activa y vigente para registrar estudiantes.");
        }
    }

    private void updateFoundation(String studentPublicId, OrganizationJpaEntity organization,
            String professionalProfilePublicId, String technologicalProfilePublicId, String technologyPublicId,
            LocalDate certificationEnrollmentDate, Long actorId) {
        Long professionalProfileId = resolveCatalogId("CERTIFICATION_PROFILE_CATALOG", "CERTIFICATION_PROFILE_ID",
                professionalProfilePublicId);
        Long technologicalProfileId = resolveCatalogId("TECHNOLOGICAL_PROFILE_CATALOG", "TECHNOLOGICAL_PROFILE_ID",
                technologicalProfilePublicId);
        Long technologyId = resolveCatalogId("QUESTION_TECHNOLOGY", "TECHNOLOGY_ID", technologyPublicId);
        boolean certificationsEnabled = organization.isAppliesCertifications();
        jdbc.update("""
            UPDATE STUDENT
               SET PROFESSIONAL_PROFILE_ID = :professionalProfileId,
                   TECHNOLOGICAL_PROFILE_ID = :technologicalProfileId,
                   TECHNOLOGY_ID = :technologyId,
                   CERTIFICATIONS_ENABLED = :certificationsEnabled,
                   CERTIFICATION_ENROLLMENT_DATE = :enrollmentDate,
                   UPDATED_BY = :actorId,
                   UPDATED_AT = SYSTIMESTAMP
             WHERE PUBLIC_ID = :studentPublicId
            """, new MapSqlParameterSource()
                .addValue("professionalProfileId", professionalProfileId)
                .addValue("technologicalProfileId", technologicalProfileId)
                .addValue("technologyId", technologyId)
                .addValue("certificationsEnabled", certificationsEnabled ? 1 : 0)
                .addValue("enrollmentDate", certificationsEnabled ? certificationEnrollmentDate : null)
                .addValue("actorId", actorId)
                .addValue("studentPublicId", studentPublicId));
    }

    private Long resolveCatalogId(String table, String idColumn, String publicId) {
        if (publicId == null || publicId.isBlank()) return null;
        List<Long> ids = jdbc.query("SELECT " + idColumn + " FROM " + table
                + " WHERE PUBLIC_ID = :publicId AND STATUS = 'ACTIVE'", Map.of("publicId", publicId.trim()),
                (rs, rowNum) -> rs.getLong(1));
        if (ids.isEmpty()) throw new BusinessException("STUDENT_CATALOG_INVALID",
                "El perfil o la tecnología seleccionada no está disponible.");
        return ids.getFirst();
    }

    private List<CatalogRef> catalog(String table, String publicIdColumn, String codeColumn, String nameColumn,
            String orderColumn) {
        return jdbc.query("SELECT " + publicIdColumn + " PUBLIC_ID, " + codeColumn + " CODE, "
                + nameColumn + " NAME FROM " + table + " WHERE STATUS = 'ACTIVE' ORDER BY "
                + orderColumn + ", " + nameColumn,
                (rs, rowNum) -> new CatalogRef(rs.getString("PUBLIC_ID"), rs.getString("CODE"), rs.getString("NAME")));
    }

    private String buildWhere(TenantContext tenant, SearchCriteria criteria, MapSqlParameterSource params) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1 ");
        if (tenant.globalAdministrator()) {
            if (criteria.organizationPublicId() != null && !criteria.organizationPublicId().isBlank()) {
                where.append(" AND o.PUBLIC_ID = :organizationPublicId ");
                params.addValue("organizationPublicId", criteria.organizationPublicId().trim());
            }
            where.append(" AND o.ORGANIZATION_TYPE = 'CUSTOMER' ");
        } else {
            where.append(" AND s.ORGANIZATION_ID = :tenantOrganizationId ");
            params.addValue("tenantOrganizationId", tenant.organizationId());
        }
        if (!criteria.includeDeleted()) where.append(" AND s.STATUS <> 'DELETED' ");
        if (criteria.query() != null && !criteria.query().isBlank()) {
            where.append(" AND (LOWER(s.DISPLAY_NAME) LIKE :query OR LOWER(s.EMAIL) LIKE :query OR LOWER(s.STUDENT_CODE) LIKE :query) ");
            params.addValue("query", "%" + criteria.query().trim().toLowerCase() + "%");
        }
        appendStatus(where, criteria.status());
        appendCatalogFilter(where, params, "profile.PUBLIC_ID", "profilePublicId", criteria.profilePublicId());
        appendCatalogFilter(where, params, "tech_profile.PUBLIC_ID", "technologicalProfilePublicId",
                criteria.technologicalProfilePublicId());
        appendCatalogFilter(where, params, "technology.PUBLIC_ID", "technologyPublicId", criteria.technologyPublicId());
        if (criteria.certificationsEnabled() != null) {
            where.append(" AND s.CERTIFICATIONS_ENABLED = :certificationsEnabled ");
            params.addValue("certificationsEnabled", criteria.certificationsEnabled() ? 1 : 0);
        }
        return where.toString();
    }

    private void appendCatalogFilter(StringBuilder where, MapSqlParameterSource params, String column,
            String parameter, String value) {
        if (value != null && !value.isBlank()) {
            where.append(" AND ").append(column).append(" = :").append(parameter).append(' ');
            params.addValue(parameter, value.trim());
        }
    }

    private void appendStatus(StringBuilder where, StudentEffectiveStatus status) {
        if (status == null) return;
        switch (status) {
            case ACTIVE -> where.append(" AND s.STATUS = 'ACTIVE' AND s.VALID_FROM <= :now AND (s.EXPIRES_AT IS NULL OR s.EXPIRES_AT > :now) ");
            case PENDING -> where.append(" AND s.STATUS = 'ACTIVE' AND s.VALID_FROM > :now ");
            case EXPIRED -> where.append(" AND s.STATUS = 'ACTIVE' AND s.EXPIRES_AT IS NOT NULL AND s.EXPIRES_AT <= :now ");
            default -> where.append(" AND s.STATUS = '").append(status.name()).append("' ");
        }
    }

    private String allowedOrder(String property, String direction) {
        String column = switch (property == null ? "" : property) {
            case "displayName" -> "LOWER(s.DISPLAY_NAME)";
            case "email" -> "LOWER(s.EMAIL)";
            case "expiresAt" -> "s.EXPIRES_AT";
            case "organization" -> "LOWER(o.ORGANIZATION_NAME)";
            default -> "NVL(s.UPDATED_AT, s.CREATED_AT)";
        };
        String order = "ASC".equalsIgnoreCase(direction) ? "ASC" : "DESC";
        return column + " " + order + ", s.STUDENT_ID DESC";
    }

    private StudentView mapStudent(ResultSet rs, int rowNum) throws SQLException {
        Instant now = clock.instant();
        StudentStatus physical = StudentStatus.valueOf(rs.getString("STATUS"));
        Instant validFrom = instant(rs, "VALID_FROM");
        Instant expiresAt = instant(rs, "EXPIRES_AT");
        StudentEffectiveStatus effective = effectiveStatus(physical, validFrom, expiresAt, now);
        return new StudentView(rs.getString("PUBLIC_ID"), rs.getString("STUDENT_CODE"), rs.getString("EMAIL"),
                rs.getString("FIRST_NAME"), rs.getString("LAST_NAME"), rs.getString("DISPLAY_NAME"), physical,
                effective, validFrom, expiresAt, rs.getBoolean("PASSWORD_CHANGE_REQUIRED"),
                instant(rs, "TEMP_PASSWORD_EXPIRES_AT"), instant(rs, "LAST_LOGIN_AT"),
                instant(rs, "ARCHIVED_AT"), instant(rs, "DELETED_AT"), rs.getString("DELETION_REASON"),
                instant(rs, "CREATED_AT"), instant(rs, "UPDATED_AT"), rs.getLong("VERSION_NO"),
                new OrganizationRef(rs.getString("ORGANIZATION_PUBLIC_ID"), rs.getString("ORGANIZATION_CODE"),
                        rs.getString("ORGANIZATION_NAME"), rs.getBoolean("APPLIES_CERTIFICATIONS")),
                ref(rs, "PROFILE_PUBLIC_ID", "PROFILE_CODE", "PROFILE_NAME"),
                ref(rs, "TECH_PROFILE_PUBLIC_ID", "TECH_PROFILE_CODE", "TECH_PROFILE_NAME"),
                ref(rs, "TECHNOLOGY_PUBLIC_ID", "TECHNOLOGY_CODE", "TECHNOLOGY_NAME"),
                rs.getBoolean("CERTIFICATIONS_ENABLED"), localDate(rs, "CERTIFICATION_ENROLLMENT_DATE"));
    }

    private CatalogRef ref(ResultSet rs, String publicId, String code, String name) throws SQLException {
        String value = rs.getString(publicId);
        return value == null ? null : new CatalogRef(value, rs.getString(code), rs.getString(name));
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private LocalDate localDate(ResultSet rs, String column) throws SQLException {
        java.sql.Date value = rs.getDate(column);
        return value == null ? null : value.toLocalDate();
    }

    private StudentEffectiveStatus effectiveStatus(StudentStatus status, Instant validFrom, Instant expiresAt, Instant now) {
        if (status == StudentStatus.DELETED) return StudentEffectiveStatus.DELETED;
        if (status == StudentStatus.ARCHIVED) return StudentEffectiveStatus.ARCHIVED;
        if (status == StudentStatus.SUSPENDED) return StudentEffectiveStatus.SUSPENDED;
        if (status == StudentStatus.INACTIVE) return StudentEffectiveStatus.INACTIVE;
        if (validFrom != null && now.isBefore(validFrom)) return StudentEffectiveStatus.PENDING;
        if (expiresAt != null && !now.isBefore(expiresAt)) return StudentEffectiveStatus.EXPIRED;
        return StudentEffectiveStatus.ACTIVE;
    }

    private void requireTenant(TenantContext tenant) {
        if (tenant == null || (!tenant.globalAdministrator() && !tenant.hasOrganization())) {
            throw new BusinessException("ORGANIZATION_CONTEXT_REQUIRED", "No existe un contexto autorizado.");
        }
    }

    private void recordFoundationAudit(StudentService.Actor actor, String event, String studentPublicId,
            OrganizationJpaEntity organization, String profile, String technologicalProfile, String technology) {
        Map<String, Object> data = new HashMap<>();
        data.put("studentPublicId", studentPublicId);
        data.put("organizationPublicId", organization.getPublicId());
        data.put("professionalProfilePublicId", profile);
        data.put("technologicalProfilePublicId", technologicalProfile);
        data.put("technologyPublicId", technology);
        data.values().removeIf(java.util.Objects::isNull);
        audit.record(actor.userId(), event, "STUDENTS", "Se actualizó la clasificación del estudiante.",
                actor.ipAddress(), actor.userAgent(), data, clock.instant());
    }

    public record SearchCriteria(String query, StudentEffectiveStatus status, boolean includeDeleted,
            String organizationPublicId, String profilePublicId, String technologicalProfilePublicId,
            String technologyPublicId, Boolean certificationsEnabled, String sort, String direction) {}
    public record CreateCommand(String organizationPublicId, String studentCode, String email, String firstName,
            String lastName, String displayName, String temporaryPassword, StudentStatus status, Instant validFrom,
            Instant expiresAt, String professionalProfilePublicId, String technologicalProfilePublicId,
            String technologyPublicId, LocalDate certificationEnrollmentDate) {}
    public record UpdateCommand(String email, String firstName, String lastName, String displayName, Instant validFrom,
            Instant expiresAt, String professionalProfilePublicId, String technologicalProfilePublicId,
            String technologyPublicId, LocalDate certificationEnrollmentDate, Long version) {}
    public record CatalogRef(String publicId, String code, String name) {}
    public record CatalogBundle(List<CatalogRef> profiles, List<CatalogRef> technologicalProfiles,
            List<CatalogRef> technologies) {}
    public record OrganizationRef(String publicId, String code, String name, boolean appliesCertifications) {}
    public record StudentView(String publicId, String studentCode, String email, String firstName, String lastName,
            String displayName, StudentStatus status, StudentEffectiveStatus effectiveStatus, Instant validFrom,
            Instant expiresAt, boolean passwordChangeRequired, Instant temporaryPasswordExpiresAt,
            Instant lastLoginAt, Instant archivedAt, Instant deletedAt, String deletionReason, Instant createdAt,
            Instant updatedAt, Long version, OrganizationRef organization, CatalogRef professionalProfile,
            CatalogRef technologicalProfile, CatalogRef technology, boolean certificationsEnabled,
            LocalDate certificationEnrollmentDate) {}
    public record PageResult(List<StudentView> content, int page, int size, long totalElements, int totalPages) {
        public PageResult { content = content == null ? List.of() : List.copyOf(content); }
    }
}
