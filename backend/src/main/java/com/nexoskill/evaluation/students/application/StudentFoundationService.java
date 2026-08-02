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
        SELECT s.PUBLIC_ID, s.STUDENT_CODE, s.CORPORATE_USER, s.EMAIL, s.FIRST_NAME, s.LAST_NAME, s.DISPLAY_NAME,
               s.STATUS, s.ACCESS_VALID_FROM, s.ACCESS_EXPIRES_ON, s.ADMISSION_DATE,
               s.PASSWORD_CHANGE_REQUIRED, s.TEMP_PASSWORD_EXPIRES_AT, s.LAST_LOGIN_AT,
               s.CREATED_AT, s.UPDATED_AT, s.VERSION_NO,
               o.PUBLIC_ID ORGANIZATION_PUBLIC_ID, o.ORGANIZATION_CODE, o.ORGANIZATION_NAME,
               o.APPLIES_CERTIFICATIONS, o.MANUAL_STUDENT_CODE,
               profile.PUBLIC_ID PROFILE_PUBLIC_ID, profile.PROFILE_CODE, profile.PROFILE_NAME,
               tech_profile.PUBLIC_ID TECH_PROFILE_PUBLIC_ID,
               tech_profile.PROFILE_CODE TECH_PROFILE_CODE, tech_profile.PROFILE_NAME TECH_PROFILE_NAME,
               s.APPLIES_TECH_CERT, s.APPLIES_DEV_SECURITY, s.APPLIES_NORMATIVE_TESTING,
               s.APPLIES_ONE, s.APPLIES_AGILE, s.APPLIES_JIRA
          FROM STUDENT s
          JOIN ORGANIZATION o ON o.ORGANIZATION_ID = s.ORGANIZATION_ID
          LEFT JOIN CERTIFICATION_PROFILE_CATALOG profile
            ON profile.CERTIFICATION_PROFILE_ID = s.PROFESSIONAL_PROFILE_ID
          LEFT JOIN TECHNOLOGICAL_PROFILE_CATALOG tech_profile
            ON tech_profile.TECHNOLOGICAL_PROFILE_ID = s.TECHNOLOGICAL_PROFILE_ID
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
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("today", java.sql.Date.valueOf(LocalDate.now(clock)))
                .addValue("offset", safePage * safeSize)
                .addValue("size", safeSize);
        String where = buildWhere(tenant, criteria, params);
        long total = jdbc.queryForObject("""
            SELECT COUNT(*)
              FROM STUDENT s
              JOIN ORGANIZATION o ON o.ORGANIZATION_ID = s.ORGANIZATION_ID
              LEFT JOIN CERTIFICATION_PROFILE_CATALOG profile
                ON profile.CERTIFICATION_PROFILE_ID = s.PROFESSIONAL_PROFILE_ID
              LEFT JOIN TECHNOLOGICAL_PROFILE_CATALOG tech_profile
                ON tech_profile.TECHNOLOGICAL_PROFILE_ID = s.TECHNOLOGICAL_PROFILE_ID
            """ + where, params, Long.class);
        if (total == 0) return new PageResult(List.of(), safePage, safeSize, 0, 0);
        List<StudentView> content = jdbc.query(BASE_SELECT + where + " ORDER BY "
                + allowedOrder(criteria.sort(), criteria.direction())
                + " OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY", params, this::mapStudent);
        int totalPages = (int) Math.ceil((double) total / safeSize);
        return new PageResult(content, safePage, safeSize, total, totalPages);
    }

    @Transactional(readOnly = true)
    public StudentView get(TenantContext tenant, String publicId) {
        TenantContext effective = effectiveTenantForStudent(tenant, publicId);
        List<StudentView> rows = jdbc.query(BASE_SELECT
                + " WHERE s.PUBLIC_ID = :publicId AND s.ORGANIZATION_ID = :organizationId AND s.STATUS <> 'DELETED'",
                new MapSqlParameterSource("publicId", publicId)
                        .addValue("organizationId", effective.organizationId()), this::mapStudent);
        if (rows.isEmpty()) throw new BusinessException("STUDENT_NOT_FOUND", "El estudiante no existe.");
        return rows.getFirst();
    }

    @Transactional
    public CreateResult create(TenantContext tenant, CreateCommand command, StudentService.Actor actor) {
        TenantContext effective = resolveCreateTenant(tenant, command.organizationPublicId());
        OrganizationJpaEntity organization = organizationRepository.findById(effective.organizationId())
                .orElseThrow(() -> new BusinessException("ORGANIZATION_NOT_FOUND", "La organización no existe."));
        validateCertificationConfiguration(organization, command.admissionDate(), command.professionalProfilePublicId(),
                command.technologicalProfilePublicId(), command.appliesTechnologicalCertification(),
                command.appliesDevelopmentSecurity(), command.appliesNormativeTesting(), command.appliesOne(),
                command.appliesAgile(), command.appliesJira());
        StudentStatus initialStatus = command.admissionDate() == null ? StudentStatus.INACTIVE : StudentStatus.ACTIVE;
        StudentService.CreateResult creation = studentService.create(effective,
                new StudentService.CreateCommand(command.email(), command.firstName(),
                        command.lastName(), command.displayName(), initialStatus,
                        command.validFrom(), command.expiresAt(), command.admissionDate(),
                        command.studentCode(), command.corporateUser()), actor);
        StudentService.StudentDetail created = creation.student();
        updateFoundation(created.publicId(), organization, command.admissionDate(),
                command.professionalProfilePublicId(), command.technologicalProfilePublicId(),
                command.appliesTechnologicalCertification(), command.appliesDevelopmentSecurity(),
                command.appliesNormativeTesting(), command.appliesOne(), command.appliesAgile(),
                command.appliesJira(), actor.userId(), null);
        recordFoundationAudit(actor, "STUDENT_FOUNDATION_CREATED", created.publicId(), organization, command);
        return new CreateResult(get(tenant, created.publicId()), creation.temporaryPassword());
    }

    @Transactional
    public StudentView update(TenantContext tenant, String publicId, UpdateCommand command, StudentService.Actor actor) {
        TenantContext effective = effectiveTenantForStudent(tenant, publicId);
        OrganizationJpaEntity organization = organizationRepository.findById(effective.organizationId())
                .orElseThrow(() -> new BusinessException("ORGANIZATION_NOT_FOUND", "La organización no existe."));
        StudentView previous = get(tenant, publicId);
        validateCertificationConfiguration(organization, command.admissionDate(), command.professionalProfilePublicId(),
                command.technologicalProfilePublicId(), command.appliesTechnologicalCertification(),
                command.appliesDevelopmentSecurity(), command.appliesNormativeTesting(), command.appliesOne(),
                command.appliesAgile(), command.appliesJira());
        validateInactiveCertificationChanges(previous, command);
        studentService.update(effective, publicId,
                new StudentService.UpdateCommand(command.email(), command.firstName(), command.lastName(),
                        command.displayName(), command.validFrom(), command.expiresAt(), command.admissionDate(),
                        command.studentCode(), command.corporateUser(), command.version()), actor);
        updateFoundation(publicId, organization, command.admissionDate(), command.professionalProfilePublicId(),
                command.technologicalProfilePublicId(), command.appliesTechnologicalCertification(),
                command.appliesDevelopmentSecurity(), command.appliesNormativeTesting(), command.appliesOne(),
                command.appliesAgile(), command.appliesJira(), actor.userId(), previous.admissionDate());
        recordApplicabilityChanges(actor, previous, command, organization);
        recordFoundationAudit(actor, "STUDENT_FOUNDATION_UPDATED", publicId, organization, command);
        return get(tenant, publicId);
    }

    private void validateInactiveCertificationChanges(StudentView previous, UpdateCommand command) {
        if (command.admissionDate() != null) return;
        boolean changed = !java.util.Objects.equals(
                    previous.professionalProfile() == null ? null : previous.professionalProfile().publicId(),
                    command.professionalProfilePublicId())
                || !java.util.Objects.equals(
                    previous.technologicalProfile() == null ? null : previous.technologicalProfile().publicId(),
                    command.technologicalProfilePublicId())
                || previous.appliesTechnologicalCertification() != command.appliesTechnologicalCertification()
                || previous.appliesDevelopmentSecurity() != command.appliesDevelopmentSecurity()
                || previous.appliesNormativeTesting() != command.appliesNormativeTesting()
                || previous.appliesOne() != command.appliesOne()
                || previous.appliesAgile() != command.appliesAgile()
                || previous.appliesJira() != command.appliesJira();
        if (changed) {
            throw new BusinessException("STUDENT_CERTIFICATION_INACTIVE",
                    "El colaborador se encuentra inactivo porque no tiene Fecha de alta. No es posible gestionar sus certificaciones.");
        }
    }

    @Transactional(readOnly = true)
    public CatalogBundle catalogs(TenantContext tenant, String organizationPublicId) {
        OrganizationJpaEntity organization = resolveCatalogOrganization(tenant, organizationPublicId);
        OrganizationRef organizationRef = new OrganizationRef(organization.getPublicId(), organization.getCode(),
                organization.getName(), organization.isAppliesCertifications(), organization.isManualStudentCode());
        if (!organization.isAppliesCertifications()) {
            return new CatalogBundle(organizationRef, false, List.of(), List.of());
        }
        return new CatalogBundle(organizationRef, true,
                catalog("CERTIFICATION_PROFILE_CATALOG", "CERTIFICATION_PROFILE_ID", "PUBLIC_ID", "PROFILE_CODE",
                        "PROFILE_NAME", "SORT_ORDER", organization.getId()),
                catalog("TECHNOLOGICAL_PROFILE_CATALOG", "TECHNOLOGICAL_PROFILE_ID", "PUBLIC_ID", "PROFILE_CODE",
                        "PROFILE_NAME", "DISPLAY_ORDER", organization.getId()));
    }

    @Transactional(readOnly = true)
    public TenantContext effectiveTenantForStudent(TenantContext tenant, String publicId) {
        requireTenant(tenant);
        if (!tenant.globalAdministrator()) return tenant;
        List<TenantContext> rows = jdbc.query("""
            SELECT o.ORGANIZATION_ID, o.PUBLIC_ID, o.ORGANIZATION_CODE
              FROM STUDENT s
              JOIN ORGANIZATION o ON o.ORGANIZATION_ID = s.ORGANIZATION_ID
             WHERE s.PUBLIC_ID = :publicId
               AND s.STATUS <> 'DELETED'
               AND o.ORGANIZATION_TYPE = 'CUSTOMER'
            """, Map.of("publicId", publicId), (rs, rowNum) -> TenantContext.organization(
                rs.getLong("ORGANIZATION_ID"), rs.getString("PUBLIC_ID"),
                rs.getString("ORGANIZATION_CODE"), true));
        if (rows.isEmpty()) throw new BusinessException("STUDENT_NOT_FOUND", "El estudiante no existe.");
        return rows.getFirst();
    }

    private OrganizationJpaEntity resolveCatalogOrganization(TenantContext tenant, String organizationPublicId) {
        requireTenant(tenant);
        OrganizationJpaEntity organization;
        if (tenant.globalAdministrator()) {
            if (organizationPublicId != null && !organizationPublicId.isBlank()) {
                organization = organizationRepository.findByPublicId(organizationPublicId.trim())
                        .orElseThrow(() -> new BusinessException("ORGANIZATION_NOT_FOUND", "La organización no existe."));
            } else if (!tenant.globalScope() && tenant.hasOrganization()) {
                organization = organizationRepository.findById(tenant.organizationId())
                        .orElseThrow(() -> new BusinessException("ORGANIZATION_NOT_FOUND", "La organización no existe."));
            } else {
                throw new BusinessException("STUDENT_ORGANIZATION_REQUIRED",
                        "Selecciona la organización del estudiante antes de consultar sus catálogos.");
            }
        } else {
            if (organizationPublicId != null && !organizationPublicId.isBlank()) {
                throw new BusinessException("STUDENT_ORGANIZATION_FORBIDDEN",
                        "La organización se obtiene de la sesión autenticada.");
            }
            organization = organizationRepository.findById(tenant.organizationId())
                    .orElseThrow(() -> new BusinessException("ORGANIZATION_NOT_FOUND", "La organización no existe."));
        }
        validateOperationalOrganization(organization.getId());
        return organization;
    }

    private TenantContext resolveCreateTenant(TenantContext tenant, String organizationPublicId) {
        requireTenant(tenant);
        if (!tenant.globalAdministrator()) {
            if (organizationPublicId != null && !organizationPublicId.isBlank()) {
                throw new BusinessException("STUDENT_ORGANIZATION_FORBIDDEN",
                        "La organización del estudiante se obtiene de la sesión autenticada.",
                        Map.of("organizationPublicId", "No envíes una organización; se asigna automáticamente desde tu sesión."));
            }
            validateOperationalOrganization(tenant.organizationId());
            return tenant;
        }
        if (organizationPublicId == null || organizationPublicId.isBlank()) {
            throw new BusinessException("STUDENT_ORGANIZATION_REQUIRED", "Selecciona la organización del estudiante.",
                    Map.of("organizationPublicId", "Debes seleccionar una organización."));
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

    private void validateCertificationConfiguration(OrganizationJpaEntity organization, LocalDate admissionDate,
            String profile, String technologicalProfile, boolean appliesTechnological, boolean appliesDevelopment,
            boolean appliesNormative, boolean appliesOne, boolean appliesAgile, boolean appliesJira) {
        boolean hasData = notBlank(profile) || notBlank(technologicalProfile)
                || appliesTechnological || appliesDevelopment || appliesNormative || appliesOne || appliesAgile || appliesJira;
        if (!organization.isAppliesCertifications() && hasData) {
            throw new BusinessException("STUDENT_CERTIFICATIONS_NOT_ENABLED",
                    "La organización seleccionada no tiene habilitada la gestión de certificaciones.",
                    Map.of("certifications", "Retira los datos de perfil y certificación para esta organización."));
        }
    }

    private void updateFoundation(String studentPublicId, OrganizationJpaEntity organization, LocalDate admissionDate,
            String professionalProfilePublicId, String technologicalProfilePublicId, boolean appliesTechnological,
            boolean appliesDevelopment, boolean appliesNormative, boolean appliesOne, boolean appliesAgile,
            boolean appliesJira, Long actorId, LocalDate previousAdmissionDate) {
        boolean enabled = organization.isAppliesCertifications();
        Long profileId = enabled ? resolveCatalogId("CERTIFICATION_PROFILE_CATALOG", "CERTIFICATION_PROFILE_ID",
                professionalProfilePublicId, organization.getId(), "professionalProfilePublicId",
                "El perfil seleccionado no pertenece a la organización.") : null;
        Long technologicalProfileId = enabled ? resolveCatalogId("TECHNOLOGICAL_PROFILE_CATALOG",
                "TECHNOLOGICAL_PROFILE_ID", technologicalProfilePublicId, organization.getId(),
                "technologicalProfilePublicId", "El perfil tecnológico seleccionado no pertenece a la organización.") : null;
        boolean anyArea = enabled && (appliesTechnological || appliesDevelopment || appliesNormative || appliesOne || appliesAgile || appliesJira);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("professionalProfileId", profileId)
                .addValue("technologicalProfileId", technologicalProfileId)
                .addValue("enabled", anyArea ? 1 : 0)
                .addValue("admissionDate", admissionDate != null
                        ? java.sql.Date.valueOf(admissionDate) : null, java.sql.Types.DATE)
                .addValue("appliesTechnological", enabled && appliesTechnological ? 1 : 0)
                .addValue("appliesDevelopment", enabled && appliesDevelopment ? 1 : 0)
                .addValue("appliesNormative", enabled && appliesNormative ? 1 : 0)
                .addValue("appliesOne", enabled && appliesOne ? 1 : 0)
                .addValue("appliesAgile", enabled && appliesAgile ? 1 : 0)
                .addValue("appliesJira", enabled && appliesJira ? 1 : 0)
                .addValue("actorId", actorId)
                .addValue("studentPublicId", studentPublicId);
        jdbc.update("""
            UPDATE STUDENT
               SET PROFESSIONAL_PROFILE_ID = :professionalProfileId,
                   TECHNOLOGICAL_PROFILE_ID = :technologicalProfileId,
                   CERTIFICATIONS_ENABLED = :enabled,
                   ADMISSION_DATE = :admissionDate,
                   APPLIES_TECH_CERT = :appliesTechnological,
                   APPLIES_DEV_SECURITY = :appliesDevelopment,
                   APPLIES_NORMATIVE_TESTING = :appliesNormative,
                   APPLIES_ONE = :appliesOne,
                   APPLIES_AGILE = :appliesAgile,
                   APPLIES_JIRA = :appliesJira,
                   UPDATED_BY = :actorId,
                   UPDATED_AT = SYSTIMESTAMP
             WHERE PUBLIC_ID = :studentPublicId
            """, params);
        synchronizeCycleApplicability(studentPublicId, params);
        boolean admissionDateChanged = !java.util.Objects.equals(previousAdmissionDate, admissionDate);
        if (admissionDateChanged && enabled && admissionDate != null) {
            recalculatePendingDeadlines(studentPublicId, admissionDate);
        } else if (admissionDateChanged && previousAdmissionDate != null && admissionDate == null) {
            clearAdmissionBasedDeadlines(studentPublicId, previousAdmissionDate);
        }
    }

    private void synchronizeCycleApplicability(String studentPublicId, MapSqlParameterSource params) {
        Map<String, Integer> flags = Map.of(
                "TECHNOLOGICAL", params.getValue("appliesTechnological").equals(1) ? 1 : 0,
                "DEVELOPMENT_SECURITY", params.getValue("appliesDevelopment").equals(1) ? 1 : 0,
                "NORMATIVE_TESTING", params.getValue("appliesNormative").equals(1) ? 1 : 0,
                "ONE", params.getValue("appliesOne").equals(1) ? 1 : 0,
                "AGILE", params.getValue("appliesAgile").equals(1) ? 1 : 0,
                "JIRA", params.getValue("appliesJira").equals(1) ? 1 : 0);
        flags.forEach((type, active) -> jdbc.update("""
            UPDATE STUDENT_CERTIFICATION_CYCLE c
               SET ACTIVE = CASE
                       WHEN :active = 0 THEN 0
                       WHEN c.TRACKING_STATUS <> 'CANCELLED' THEN 1
                       ELSE c.ACTIVE
                   END,
                   UPDATED_AT = SYSTIMESTAMP
             WHERE c.STUDENT_ID = (SELECT STUDENT_ID FROM STUDENT WHERE PUBLIC_ID = :studentPublicId)
               AND c.CERTIFICATION_TYPE = :type
            """, Map.of("active", active, "studentPublicId", studentPublicId, "type", type)));
    }

    private void recalculatePendingDeadlines(String studentPublicId, LocalDate admissionDate) {
        jdbc.update("""
            UPDATE STUDENT_CERTIFICATION_CYCLE c
               SET c.DEADLINE_DATE = (
                    SELECT ADD_MONTHS(:admissionDate, NVL(p.DEADLINE_MONTHS, 0)) + NVL(p.DEADLINE_DAYS, 0)
                      FROM ORGANIZATION_CERTIFICATION_POLICY p
                     WHERE p.ORGANIZATION_ID = c.ORGANIZATION_ID
                       AND p.CERTIFICATION_TYPE = c.CERTIFICATION_TYPE
                       AND p.STATUS = 'ACTIVE'
               ), c.UPDATED_AT = SYSTIMESTAMP
             WHERE c.STUDENT_ID = (SELECT STUDENT_ID FROM STUDENT WHERE PUBLIC_ID = :studentPublicId)
               AND c.CERTIFICATION_TYPE NOT IN ('ONE','AGILE','JIRA')
               AND c.APPLICATION_DATE IS NULL
               AND c.LAST_APPROVED_APPLICATION_DATE IS NULL
               AND NVL(c.APPROVED, 0) = 0
               AND EXISTS (SELECT 1 FROM ORGANIZATION_CERTIFICATION_POLICY p
                            WHERE p.ORGANIZATION_ID = c.ORGANIZATION_ID
                              AND p.CERTIFICATION_TYPE = c.CERTIFICATION_TYPE
                              AND p.STATUS = 'ACTIVE'
                              AND (p.DEADLINE_MONTHS IS NOT NULL OR p.DEADLINE_DAYS IS NOT NULL))
            """, Map.of("admissionDate", java.sql.Date.valueOf(admissionDate), "studentPublicId", studentPublicId));
    }

    private void clearAdmissionBasedDeadlines(String studentPublicId, LocalDate previousAdmissionDate) {
        jdbc.update("""
            UPDATE STUDENT_CERTIFICATION_CYCLE c
               SET c.DEADLINE_DATE = NULL,
                   c.UPDATED_AT = SYSTIMESTAMP
             WHERE c.STUDENT_ID = (SELECT STUDENT_ID FROM STUDENT WHERE PUBLIC_ID = :studentPublicId)
               AND c.CERTIFICATION_TYPE NOT IN ('ONE','AGILE','JIRA')
               AND c.APPLICATION_DATE IS NULL
               AND c.LAST_APPROVED_APPLICATION_DATE IS NULL
               AND NVL(c.APPROVED, 0) = 0
               AND EXISTS (
                    SELECT 1
                      FROM ORGANIZATION_CERTIFICATION_POLICY p
                     WHERE p.ORGANIZATION_ID = c.ORGANIZATION_ID
                       AND p.CERTIFICATION_TYPE = c.CERTIFICATION_TYPE
                       AND p.STATUS = 'ACTIVE'
                       AND c.DEADLINE_DATE = ADD_MONTHS(:previousAdmissionDate, NVL(p.DEADLINE_MONTHS, 0))
                           + NVL(p.DEADLINE_DAYS, 0)
               )
            """, Map.of("previousAdmissionDate", java.sql.Date.valueOf(previousAdmissionDate),
                    "studentPublicId", studentPublicId));
    }

    private Long resolveCatalogId(String table, String idColumn, String publicId, Long organizationId,
            String field, String fieldMessage) {
        if (!notBlank(publicId)) return null;
        List<Long> ids = jdbc.query("SELECT " + idColumn + " FROM " + table
                + " WHERE PUBLIC_ID = :publicId AND STATUS = 'ACTIVE'"
                + " AND (CONTENT_SCOPE = 'GLOBAL' OR (CONTENT_SCOPE = 'ORGANIZATION' AND OWNER_ORGANIZATION_ID = :organizationId))",
                new MapSqlParameterSource("publicId", publicId.trim()).addValue("organizationId", organizationId),
                (rs, rowNum) -> rs.getLong(1));
        if (ids.isEmpty()) throw new BusinessException("STUDENT_CATALOG_INVALID", fieldMessage, Map.of(field, fieldMessage));
        return ids.getFirst();
    }

    private List<CatalogRef> catalog(String table, String idColumn, String publicIdColumn, String codeColumn,
            String nameColumn, String orderColumn, Long organizationId) {
        return jdbc.query("SELECT " + publicIdColumn + " PUBLIC_ID, " + codeColumn + " CODE, "
                + nameColumn + " NAME FROM " + table
                + " WHERE STATUS = 'ACTIVE' AND (CONTENT_SCOPE = 'GLOBAL' OR (CONTENT_SCOPE = 'ORGANIZATION'"
                + " AND OWNER_ORGANIZATION_ID = :organizationId)) ORDER BY " + orderColumn + ", " + nameColumn,
                Map.of("organizationId", organizationId),
                (rs, rowNum) -> new CatalogRef(rs.getString("PUBLIC_ID"), rs.getString("CODE"), rs.getString("NAME")));
    }

    private String buildWhere(TenantContext tenant, SearchCriteria criteria, MapSqlParameterSource params) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1 ");
        if (tenant.globalAdministrator()) {
            if (notBlank(criteria.organizationPublicId())) {
                where.append(" AND o.PUBLIC_ID = :organizationPublicId ");
                params.addValue("organizationPublicId", criteria.organizationPublicId().trim());
            }
            where.append(" AND o.ORGANIZATION_TYPE = 'CUSTOMER' ");
        } else {
            where.append(" AND s.ORGANIZATION_ID = :tenantOrganizationId ");
            params.addValue("tenantOrganizationId", tenant.organizationId());
        }
        where.append(" AND s.STATUS <> 'DELETED' ");
        if (notBlank(criteria.query())) {
            where.append(" AND (LOWER(s.DISPLAY_NAME) LIKE :query OR LOWER(s.EMAIL) LIKE :query "
                    + "OR LOWER(s.STUDENT_CODE) LIKE :query OR LOWER(s.CORPORATE_USER) LIKE :query) ");
            params.addValue("query", "%" + criteria.query().trim().toLowerCase() + "%");
        }
        appendStatus(where, criteria.status());
        appendCatalogFilter(where, params, "profile.PUBLIC_ID", "profilePublicId", criteria.profilePublicId());
        appendCatalogFilter(where, params, "tech_profile.PUBLIC_ID", "technologicalProfilePublicId",
                criteria.technologicalProfilePublicId());
        if (criteria.certificationsEnabled() != null) {
            where.append(" AND s.CERTIFICATIONS_ENABLED = :certificationsEnabled ");
            params.addValue("certificationsEnabled", criteria.certificationsEnabled() ? 1 : 0);
        }
        return where.toString();
    }

    private void appendCatalogFilter(StringBuilder where, MapSqlParameterSource params, String column,
            String parameter, String value) {
        if (notBlank(value)) {
            where.append(" AND ").append(column).append(" = :").append(parameter).append(' ');
            params.addValue(parameter, value.trim());
        }
    }

    private void appendStatus(StringBuilder where, StudentEffectiveStatus status) {
        if (status == null) return;
        switch (status) {
            case ACTIVE -> where.append(" AND s.STATUS = 'ACTIVE' AND s.ADMISSION_DATE IS NOT NULL AND s.ACCESS_VALID_FROM <= :today AND s.ACCESS_EXPIRES_ON >= :today ");
            case EXPIRED -> where.append(" AND (s.STATUS = 'EXPIRED' OR (s.STATUS = 'ACTIVE' AND s.ACCESS_EXPIRES_ON < :today)) ");
            case INACTIVE -> where.append(" AND (s.STATUS = 'INACTIVE' OR s.ADMISSION_DATE IS NULL) ");
            case DELETED -> where.append(" AND 1 = 0 ");
        }
    }

    private String allowedOrder(String property, String direction) {
        String column = switch (property == null ? "" : property) {
            case "displayName" -> "LOWER(s.DISPLAY_NAME)";
            case "email" -> "LOWER(s.EMAIL)";
            case "expiresAt" -> "s.ACCESS_EXPIRES_ON";
            case "admissionDate" -> "s.ADMISSION_DATE";
            case "organization" -> "LOWER(o.ORGANIZATION_NAME)";
            default -> "NVL(s.UPDATED_AT, s.CREATED_AT)";
        };
        return column + ("ASC".equalsIgnoreCase(direction) ? " ASC" : " DESC") + ", s.STUDENT_ID DESC";
    }

    private StudentView mapStudent(ResultSet rs, int rowNum) throws SQLException {
        Instant now = clock.instant();
        StudentStatus physical = StudentStatus.valueOf(rs.getString("STATUS"));
        LocalDate validFrom = localDate(rs, "ACCESS_VALID_FROM");
        LocalDate expiresAt = localDate(rs, "ACCESS_EXPIRES_ON");
        LocalDate admissionDate = localDate(rs, "ADMISSION_DATE");
        StudentEffectiveStatus effective = effectiveStatus(physical, admissionDate, expiresAt, LocalDate.now(clock));
        boolean organizationCertifications = rs.getBoolean("APPLIES_CERTIFICATIONS");
        return new StudentView(rs.getString("PUBLIC_ID"), rs.getString("STUDENT_CODE"), rs.getString("CORPORATE_USER"), rs.getString("EMAIL"),
                rs.getString("FIRST_NAME"), rs.getString("LAST_NAME"), rs.getString("DISPLAY_NAME"), physical,
                effective, validFrom, expiresAt, admissionDate,
                rs.getBoolean("PASSWORD_CHANGE_REQUIRED"), instant(rs, "TEMP_PASSWORD_EXPIRES_AT"),
                instant(rs, "LAST_LOGIN_AT"), instant(rs, "CREATED_AT"), instant(rs, "UPDATED_AT"),
                rs.getLong("VERSION_NO"), new OrganizationRef(rs.getString("ORGANIZATION_PUBLIC_ID"),
                        rs.getString("ORGANIZATION_CODE"), rs.getString("ORGANIZATION_NAME"), organizationCertifications,
                        rs.getBoolean("MANUAL_STUDENT_CODE")),
                organizationCertifications ? ref(rs, "PROFILE_PUBLIC_ID", "PROFILE_CODE", "PROFILE_NAME") : null,
                organizationCertifications ? ref(rs, "TECH_PROFILE_PUBLIC_ID", "TECH_PROFILE_CODE", "TECH_PROFILE_NAME") : null,
                organizationCertifications, organizationCertifications && rs.getBoolean("APPLIES_TECH_CERT"),
                organizationCertifications && rs.getBoolean("APPLIES_DEV_SECURITY"),
                organizationCertifications && rs.getBoolean("APPLIES_NORMATIVE_TESTING"),
                organizationCertifications && rs.getBoolean("APPLIES_ONE"),
                organizationCertifications && rs.getBoolean("APPLIES_AGILE"),
                organizationCertifications && rs.getBoolean("APPLIES_JIRA"));
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

    private StudentEffectiveStatus effectiveStatus(StudentStatus status, LocalDate admissionDate, LocalDate expiresAt, LocalDate today) {
        if (status == StudentStatus.DELETED) return StudentEffectiveStatus.DELETED;
        if (admissionDate == null || status == StudentStatus.INACTIVE) return StudentEffectiveStatus.INACTIVE;
        if (status == StudentStatus.EXPIRED || (expiresAt != null && today.isAfter(expiresAt))) return StudentEffectiveStatus.EXPIRED;
        return StudentEffectiveStatus.ACTIVE;
    }

    private void requireTenant(TenantContext tenant) {
        if (tenant == null || (!tenant.globalAdministrator() && !tenant.hasOrganization())) {
            throw new BusinessException("ORGANIZATION_CONTEXT_REQUIRED", "No existe un contexto autorizado.");
        }
    }


    private void recordApplicabilityChanges(StudentService.Actor actor, StudentView previous,
            UpdateCommand command, OrganizationJpaEntity organization) {
        recordApplicabilityChange(actor, previous.publicId(), organization, "TECHNOLOGICAL",
                previous.appliesTechnologicalCertification(), command.appliesTechnologicalCertification());
        recordApplicabilityChange(actor, previous.publicId(), organization, "DEVELOPMENT_SECURITY",
                previous.appliesDevelopmentSecurity(), command.appliesDevelopmentSecurity());
        recordApplicabilityChange(actor, previous.publicId(), organization, "NORMATIVE_TESTING",
                previous.appliesNormativeTesting(), command.appliesNormativeTesting());
        recordApplicabilityChange(actor, previous.publicId(), organization, "ONE",
                previous.appliesOne(), command.appliesOne());
        recordApplicabilityChange(actor, previous.publicId(), organization, "AGILE",
                previous.appliesAgile(), command.appliesAgile());
        recordApplicabilityChange(actor, previous.publicId(), organization, "JIRA",
                previous.appliesJira(), command.appliesJira());
    }

    private void recordApplicabilityChange(StudentService.Actor actor, String studentPublicId,
            OrganizationJpaEntity organization, String area, boolean previous, boolean current) {
        if (previous == current) return;
        Map<String, Object> data = new HashMap<>();
        data.put("studentPublicId", studentPublicId);
        data.put("organizationPublicId", organization.getPublicId());
        data.put("area", area);
        data.put("previousValue", previous);
        data.put("newValue", current);
        data.put("historyPreserved", true);
        audit.record(actor.userId(), "STUDENT_CERTIFICATION_APPLICABILITY_CHANGED", "STUDENTS",
                "Se modificó la aplicabilidad de un seguimiento de certificación.",
                actor.ipAddress(), actor.userAgent(), data, clock.instant());
    }

    private void recordFoundationAudit(StudentService.Actor actor, String event, String studentPublicId,
            OrganizationJpaEntity organization, Object command) {
        Map<String, Object> data = new HashMap<>();
        data.put("studentPublicId", studentPublicId);
        data.put("organizationPublicId", organization.getPublicId());
        if (command instanceof CreateCommand value) {
            addSafeFoundationData(data, value.admissionDate(), value.professionalProfilePublicId(),
                    value.technologicalProfilePublicId(), value.appliesTechnologicalCertification(),
                    value.appliesDevelopmentSecurity(), value.appliesNormativeTesting(), value.appliesOne(),
                    value.appliesAgile(), value.appliesJira());
        } else if (command instanceof UpdateCommand value) {
            addSafeFoundationData(data, value.admissionDate(), value.professionalProfilePublicId(),
                    value.technologicalProfilePublicId(), value.appliesTechnologicalCertification(),
                    value.appliesDevelopmentSecurity(), value.appliesNormativeTesting(), value.appliesOne(),
                    value.appliesAgile(), value.appliesJira());
        }
        audit.record(actor.userId(), event, "STUDENTS", "Se actualizó la clasificación y seguimiento inicial del estudiante.",
                actor.ipAddress(), actor.userAgent(), data, clock.instant());
    }

    private void addSafeFoundationData(Map<String, Object> data, LocalDate admissionDate,
            String professionalProfilePublicId, String technologicalProfilePublicId,
            boolean appliesTechnological, boolean appliesDevelopment, boolean appliesNormative,
            boolean appliesOne, boolean appliesAgile, boolean appliesJira) {
        data.put("admissionDate", admissionDate == null ? "" : admissionDate.toString());
        data.put("professionalProfilePublicId", professionalProfilePublicId == null ? "" : professionalProfilePublicId);
        data.put("technologicalProfilePublicId", technologicalProfilePublicId == null ? "" : technologicalProfilePublicId);
        data.put("appliesTechnologicalCertification", appliesTechnological);
        data.put("appliesDevelopmentSecurity", appliesDevelopment);
        data.put("appliesNormativeTesting", appliesNormative);
        data.put("appliesOne", appliesOne);
        data.put("appliesAgile", appliesAgile);
        data.put("appliesJira", appliesJira);
    }

    private boolean notBlank(String value) { return value != null && !value.isBlank(); }

    public record SearchCriteria(String query, StudentEffectiveStatus status, boolean includeDeleted,
            String organizationPublicId, String profilePublicId, String technologicalProfilePublicId,
            String technologyPublicId, Boolean certificationsEnabled, String sort, String direction) {}
    public record CreateCommand(String organizationPublicId, String email, String firstName,
            String lastName, String displayName, StudentStatus status, LocalDate validFrom,
            LocalDate expiresAt, LocalDate admissionDate, String studentCode, String corporateUser,
            String professionalProfilePublicId,
            String technologicalProfilePublicId, boolean appliesTechnologicalCertification,
            boolean appliesDevelopmentSecurity, boolean appliesNormativeTesting, boolean appliesOne,
            boolean appliesAgile, boolean appliesJira) {
        public CreateCommand(String organizationPublicId, String email, String firstName, String lastName,
                String displayName, StudentStatus status, LocalDate validFrom, LocalDate expiresAt,
                LocalDate admissionDate, String professionalProfilePublicId, String technologicalProfilePublicId,
                boolean appliesTechnologicalCertification, boolean appliesDevelopmentSecurity,
                boolean appliesNormativeTesting, boolean appliesOne, boolean appliesAgile, boolean appliesJira) {
            this(organizationPublicId, email, firstName, lastName, displayName, status, validFrom, expiresAt,
                    admissionDate, null, null, professionalProfilePublicId, technologicalProfilePublicId,
                    appliesTechnologicalCertification, appliesDevelopmentSecurity, appliesNormativeTesting,
                    appliesOne, appliesAgile, appliesJira);
        }
    }
    public record UpdateCommand(String email, String firstName, String lastName, String displayName,
            LocalDate validFrom, LocalDate expiresAt, LocalDate admissionDate, String studentCode,
            String corporateUser, String professionalProfilePublicId,
            String technologicalProfilePublicId, boolean appliesTechnologicalCertification,
            boolean appliesDevelopmentSecurity, boolean appliesNormativeTesting, boolean appliesOne,
            boolean appliesAgile, boolean appliesJira, Long version) {
        public UpdateCommand(String email, String firstName, String lastName, String displayName,
                LocalDate validFrom, LocalDate expiresAt, LocalDate admissionDate,
                String professionalProfilePublicId, String technologicalProfilePublicId,
                boolean appliesTechnologicalCertification, boolean appliesDevelopmentSecurity,
                boolean appliesNormativeTesting, boolean appliesOne, boolean appliesAgile,
                boolean appliesJira, Long version) {
            this(email, firstName, lastName, displayName, validFrom, expiresAt, admissionDate,
                    null, null, professionalProfilePublicId, technologicalProfilePublicId,
                    appliesTechnologicalCertification, appliesDevelopmentSecurity, appliesNormativeTesting,
                    appliesOne, appliesAgile, appliesJira, version);
        }
    }
    public record CreateResult(StudentView student, String temporaryPassword) {}
    public record CatalogRef(String publicId, String code, String name) {}
    public record CatalogBundle(OrganizationRef organization, boolean appliesCertifications,
            List<CatalogRef> profiles, List<CatalogRef> technologicalProfiles) {}
    public record OrganizationRef(String publicId, String code, String name, boolean appliesCertifications,
            boolean manualStudentCode) {}
    public record StudentView(String publicId, String studentCode, String corporateUser, String email, String firstName, String lastName,
            String displayName, StudentStatus status, StudentEffectiveStatus effectiveStatus, LocalDate validFrom,
            LocalDate expiresAt, LocalDate admissionDate, boolean passwordChangeRequired,
            Instant temporaryPasswordExpiresAt, Instant lastLoginAt, Instant createdAt, Instant updatedAt,
            Long version, OrganizationRef organization, CatalogRef professionalProfile,
            CatalogRef technologicalProfile, boolean certificationsEnabled,
            boolean appliesTechnologicalCertification, boolean appliesDevelopmentSecurity,
            boolean appliesNormativeTesting, boolean appliesOne, boolean appliesAgile, boolean appliesJira) {}
    public record PageResult(List<StudentView> content, int page, int size, long totalElements, int totalPages) {
        public PageResult { content = content == null ? List.of() : List.copyOf(content); }
    }
}
