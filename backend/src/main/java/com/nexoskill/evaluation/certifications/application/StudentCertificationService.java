package com.nexoskill.evaluation.certifications.application;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.authentication.infrastructure.security.AuthenticatedUser;
import com.nexoskill.evaluation.certifications.application.CertificationModels.*;
import com.nexoskill.evaluation.certifications.domain.CertificationExamStatus;
import com.nexoskill.evaluation.certifications.domain.CertificationLevel;
import com.nexoskill.evaluation.certifications.domain.CertificationLifecycleCalculator;
import com.nexoskill.evaluation.certifications.domain.CertificationProcessType;
import com.nexoskill.evaluation.certifications.domain.CertificationTrackingStatus;
import com.nexoskill.evaluation.certifications.domain.CertificationType;
import com.nexoskill.evaluation.certifications.domain.CertificationValidityStatus;
import com.nexoskill.evaluation.organizations.domain.model.OrganizationType;
import com.nexoskill.evaluation.organizations.domain.model.TenantContext;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationJpaEntity;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationRepository;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import com.nexoskill.evaluation.students.domain.StudentStatus;
import com.nexoskill.evaluation.students.infrastructure.persistence.StudentJpaEntity;
import com.nexoskill.evaluation.students.infrastructure.persistence.StudentRepository;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentCertificationService {
    private final StudentRepository students;
    private final OrganizationRepository organizations;
    private final NamedParameterJdbcTemplate jdbc;
    private final AuditLogPort audit;
    private final Clock clock;

    public StudentCertificationService(StudentRepository students, OrganizationRepository organizations,
            NamedParameterJdbcTemplate jdbc, AuditLogPort audit, Clock clock) {
        this.students = students;
        this.organizations = organizations;
        this.jdbc = jdbc;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Availability availability(TenantContext tenant, AuthenticatedUser actor) {
        if (tenant == null || !tenant.hasOrganization()) {
            return new Availability(false, hasOperationalRole(actor), null, null);
        }
        OrganizationJpaEntity organization = organizations.findById(tenant.organizationId()).orElse(null);
        if (organization == null) return new Availability(false, hasOperationalRole(actor), null, null);
        return new Availability(organization.getOrganizationType() == OrganizationType.CUSTOMER
                && organization.isAppliesCertifications(), hasOperationalRole(actor),
                organization.getPublicId(), organization.getName());
    }

    @Transactional(readOnly = true)
    public Catalogs catalogs(TenantContext tenant, AuthenticatedUser actor) {
        Scope scope = requireTenantOrganization(tenant, actor);
        return catalogs(scope.organizationId());
    }

    @Transactional(readOnly = true)
    public Catalogs catalogs(TenantContext tenant, String studentPublicId, AuthenticatedUser actor) {
        Scope scope = resolveStudentScope(tenant, studentPublicId, actor, false);
        return catalogs(scope.organizationId());
    }

    @Transactional(readOnly = true)
    public StudentCertificationDetail get(TenantContext tenant, String studentPublicId, AuthenticatedUser actor) {
        Scope scope = resolveStudentScope(tenant, studentPublicId, actor, true);
        return detail(scope);
    }

    @Transactional
    public StudentCertificationDetail save(TenantContext tenant, String studentPublicId, SaveCommand command,
            AuthenticatedUser actor, String ipAddress, String userAgent) {
        Scope scope = resolveStudentScope(tenant, studentPublicId, actor, true);
        if (command == null || command.applicability() == null) {
            throw new BusinessException("CERTIFICATION_CONFIGURATION_REQUIRED",
                    "La configuración de certificaciones es obligatoria.");
        }
        updateApplicability(scope, command.applicability(), actor.internalId());
        if (command.cycles() != null) {
            for (CycleCommand cycle : command.cycles()) {
                if (cycle == null) continue;
                String cyclePublicId;
                if (cycle.publicId() == null || cycle.publicId().isBlank()) {
                    cyclePublicId = createCycle(scope, cycle, actor.internalId());
                } else {
                    cyclePublicId = cycle.publicId();
                    updateCycle(scope, cyclePublicId, cycle, actor.internalId());
                }
                for (AttemptCommand attempt : cycle.attempts()) {
                    if (attempt == null) continue;
                    if (attempt.publicId() == null || attempt.publicId().isBlank()) {
                        addAttemptInternal(scope, cyclePublicId, attempt, actor.internalId());
                    } else {
                        updateAttemptInternal(scope, cyclePublicId, attempt.publicId(), attempt, actor.internalId());
                    }
                }
            }
        }
        audit(actor.internalId(), "STUDENT_CERTIFICATIONS_UPDATED", scope, null,
                "Se guardó la administración de certificaciones del estudiante.", ipAddress, userAgent);
        return detail(scope);
    }

    @Transactional
    public CycleView createCycle(TenantContext tenant, String studentPublicId, CycleCommand command,
            AuthenticatedUser actor, String ipAddress, String userAgent) {
        Scope scope = resolveStudentScope(tenant, studentPublicId, actor, true);
        String publicId = createCycle(scope, command, actor.internalId());
        audit(actor.internalId(), "STUDENT_CERTIFICATION_CYCLE_CREATED", scope, publicId,
                "Se creó un ciclo de certificación.", ipAddress, userAgent);
        return cycle(scope, publicId);
    }

    @Transactional
    public CycleView updateCycle(TenantContext tenant, String studentPublicId, String cyclePublicId,
            CycleCommand command, AuthenticatedUser actor, String ipAddress, String userAgent) {
        Scope scope = resolveStudentScope(tenant, studentPublicId, actor, true);
        updateCycle(scope, cyclePublicId, command, actor.internalId());
        audit(actor.internalId(), "STUDENT_CERTIFICATION_CYCLE_UPDATED", scope, cyclePublicId,
                "Se actualizó un ciclo de certificación.", ipAddress, userAgent);
        return cycle(scope, cyclePublicId);
    }

    @Transactional
    public CycleView makePrimary(TenantContext tenant, String studentPublicId, String cyclePublicId,
            AuthenticatedUser actor, String ipAddress, String userAgent) {
        Scope scope = resolveStudentScope(tenant, studentPublicId, actor, true);
        CycleRow row = requireCycle(scope, cyclePublicId);
        if (row.type() != CertificationType.TECHNOLOGICAL || !row.active()) {
            throw new BusinessException("CERTIFICATION_PRIMARY_INVALID",
                    "Solamente una certificación tecnológica activa puede ser principal.");
        }
        jdbc.update("""
            UPDATE STUDENT_CERTIFICATION_CYCLE
               SET IS_PRIMARY = 0, UPDATED_BY = :actorId, UPDATED_AT = SYSTIMESTAMP
             WHERE STUDENT_ID = :studentId AND CERTIFICATION_TYPE = 'TECHNOLOGICAL'
            """, Map.of("actorId", actor.internalId(), "studentId", scope.student().getId()));
        jdbc.update("""
            UPDATE STUDENT_CERTIFICATION_CYCLE
               SET IS_PRIMARY = 1, UPDATED_BY = :actorId, UPDATED_AT = SYSTIMESTAMP
             WHERE PUBLIC_ID = :publicId AND STUDENT_ID = :studentId
            """, Map.of("actorId", actor.internalId(), "publicId", cyclePublicId,
                "studentId", scope.student().getId()));
        history(scope, row.id(), null, "PRIMARY_CHANGED", null, "primary=true", null, actor.internalId());
        audit(actor.internalId(), "STUDENT_CERTIFICATION_PRIMARY_CHANGED", scope, cyclePublicId,
                "Se cambió la certificación tecnológica principal.", ipAddress, userAgent);
        return cycle(scope, cyclePublicId);
    }

    @Transactional
    public CycleView cancel(TenantContext tenant, String studentPublicId, String cyclePublicId,
            String reason, AuthenticatedUser actor, String ipAddress, String userAgent) {
        Scope scope = resolveStudentScope(tenant, studentPublicId, actor, true);
        CycleRow row = requireCycle(scope, cyclePublicId);
        jdbc.update("""
            UPDATE STUDENT_CERTIFICATION_CYCLE
               SET TRACKING_STATUS = 'CANCELLED', ACTIVE = 0, UPDATED_BY = :actorId,
                   UPDATED_AT = SYSTIMESTAMP, VERSION_NO = VERSION_NO + 1
             WHERE STUDENT_CERTIFICATION_CYCLE_ID = :id
            """, Map.of("actorId", actor.internalId(), "id", row.id()));
        history(scope, row.id(), null, "CYCLE_CANCELLED", row.trackingStatus().name(), "CANCELLED",
                clean(reason, 500), actor.internalId());
        audit(actor.internalId(), "STUDENT_CERTIFICATION_CYCLE_CANCELLED", scope, cyclePublicId,
                "Se canceló un ciclo de certificación.", ipAddress, userAgent);
        return cycle(scope, cyclePublicId);
    }

    @Transactional(readOnly = true)
    public PageResult<AttemptView> attempts(TenantContext tenant, String studentPublicId, String cyclePublicId,
            int page, int size, AuthenticatedUser actor) {
        Scope scope = resolveStudentScope(tenant, studentPublicId, actor, true);
        CycleRow cycle = requireCycle(scope, cyclePublicId);
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        MapSqlParameterSource params = new MapSqlParameterSource("cycleId", cycle.id())
                .addValue("offset", safePage * safeSize).addValue("size", safeSize);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM STUDENT_CERTIFICATION_CYCLE_ATTEMPT WHERE STUDENT_CERTIFICATION_CYCLE_ID = :cycleId",
                params, Long.class);
        List<AttemptView> content = jdbc.query("""
            SELECT PUBLIC_ID, ATTEMPT_NUMBER, SCHEDULED_DATE, APPLICATION_DATE, EXAM_STATUS,
                   SCORE, APPROVED, RESULT, OBSERVATIONS, CREATED_AT, VERSION_NO
              FROM STUDENT_CERTIFICATION_CYCLE_ATTEMPT
             WHERE STUDENT_CERTIFICATION_CYCLE_ID = :cycleId
             ORDER BY ATTEMPT_NUMBER DESC
             OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY
            """, params, (rs, rowNum) -> mapAttempt(rs, cyclePublicId));
        long safeTotal = total == null ? 0 : total;
        return new PageResult<>(content, safePage, safeSize, safeTotal,
                safeTotal == 0 ? 0 : (int) Math.ceil((double) safeTotal / safeSize));
    }

    @Transactional
    public AttemptView addAttempt(TenantContext tenant, String studentPublicId, String cyclePublicId,
            AttemptCommand command, AuthenticatedUser actor, String ipAddress, String userAgent) {
        Scope scope = resolveStudentScope(tenant, studentPublicId, actor, true);
        AttemptView created = addAttemptInternal(scope, cyclePublicId, command, actor.internalId());
        audit(actor.internalId(), "STUDENT_CERTIFICATION_ATTEMPT_CREATED", scope, cyclePublicId,
                "Se registró un intento de certificación.", ipAddress, userAgent);
        return created;
    }

    @Transactional
    public AttemptView updateAttempt(TenantContext tenant, String studentPublicId, String cyclePublicId,
            String attemptPublicId, AttemptCommand command, AuthenticatedUser actor,
            String ipAddress, String userAgent) {
        Scope scope = resolveStudentScope(tenant, studentPublicId, actor, true);
        AttemptView updated = updateAttemptInternal(scope, cyclePublicId, attemptPublicId, command, actor.internalId());
        audit(actor.internalId(), "STUDENT_CERTIFICATION_ATTEMPT_UPDATED", scope, cyclePublicId,
                "Se actualizó un intento de certificación.", ipAddress, userAgent);
        return updated;
    }

    private AttemptView addAttemptInternal(Scope scope, String cyclePublicId, AttemptCommand command, Long actorId) {
        CycleRow cycle = requireCycle(scope, cyclePublicId);
        validateAttemptsSupported(cycle);
        validateAttempt(command);
        Integer next = jdbc.queryForObject("""
            SELECT NVL(MAX(ATTEMPT_NUMBER), 0) + 1
              FROM STUDENT_CERTIFICATION_CYCLE_ATTEMPT
             WHERE STUDENT_CERTIFICATION_CYCLE_ID = :cycleId
            """, Map.of("cycleId", cycle.id()), Integer.class);
        int attemptNumber = next == null ? 1 : next;
        String attemptPublicId = UUID.randomUUID().toString();
        MapSqlParameterSource params = attemptParams(scope, cycle, attemptPublicId, attemptNumber, command, actorId);
        jdbc.update("""
            INSERT INTO STUDENT_CERTIFICATION_CYCLE_ATTEMPT (
                PUBLIC_ID, STUDENT_CERTIFICATION_CYCLE_ID, ORGANIZATION_ID, ATTEMPT_NUMBER,
                SCHEDULED_DATE, APPLICATION_DATE, EXAM_STATUS, SCORE, APPROVED, RESULT,
                OBSERVATIONS, CREATED_BY, UPDATED_BY, CREATED_AT, UPDATED_AT, VERSION_NO
            ) VALUES (
                :publicId, :cycleId, :organizationId, :attemptNumber,
                :scheduledDate, :applicationDate, :examStatus, :score, :approved, :result,
                :observations, :actorId, :actorId, SYSTIMESTAMP, SYSTIMESTAMP, 0
            )
            """, params);
        Long attemptId = jdbc.queryForObject(
                "SELECT STUDENT_CERTIFICATION_CYCLE_ATTEMPT_ID FROM STUDENT_CERTIFICATION_CYCLE_ATTEMPT WHERE PUBLIC_ID = :publicId",
                Map.of("publicId", attemptPublicId), Long.class);
        applyAttemptResult(scope, cycle, command, actorId);
        history(scope, cycle.id(), attemptId, "ATTEMPT_CREATED", null,
                "attempt=" + attemptNumber + ";approved=" + command.approved(), null, actorId);
        return queryAttempt(cycle, attemptPublicId);
    }

    private AttemptView updateAttemptInternal(Scope scope, String cyclePublicId, String attemptPublicId,
            AttemptCommand command, Long actorId) {
        CycleRow cycle = requireCycle(scope, cyclePublicId);
        validateAttemptsSupported(cycle);
        validateAttempt(command);
        if (command.version() == null) {
            throw new BusinessException("CERTIFICATION_ATTEMPT_VERSION_REQUIRED",
                    "La versión del intento es obligatoria.");
        }
        MapSqlParameterSource params = attemptParams(scope, cycle, attemptPublicId, 0, command, actorId)
                .addValue("version", command.version());
        int updated = jdbc.update("""
            UPDATE STUDENT_CERTIFICATION_CYCLE_ATTEMPT
               SET SCHEDULED_DATE = :scheduledDate, APPLICATION_DATE = :applicationDate,
                   EXAM_STATUS = :examStatus, SCORE = :score, APPROVED = :approved,
                   RESULT = :result, OBSERVATIONS = :observations, UPDATED_BY = :actorId,
                   UPDATED_AT = SYSTIMESTAMP, VERSION_NO = VERSION_NO + 1
             WHERE PUBLIC_ID = :publicId AND STUDENT_CERTIFICATION_CYCLE_ID = :cycleId
               AND VERSION_NO = :version
            """, params);
        if (updated != 1) throw new BusinessException("CERTIFICATION_ATTEMPT_VERSION_CONFLICT",
                "El intento fue modificado por otra sesión. Actualiza la página.");
        Long attemptId = jdbc.queryForObject(
                "SELECT STUDENT_CERTIFICATION_CYCLE_ATTEMPT_ID FROM STUDENT_CERTIFICATION_CYCLE_ATTEMPT WHERE PUBLIC_ID = :publicId",
                Map.of("publicId", attemptPublicId), Long.class);
        applyAttemptResult(scope, cycle, command, actorId);
        history(scope, cycle.id(), attemptId, "ATTEMPT_UPDATED", null,
                "approved=" + command.approved(), null, actorId);
        return queryAttempt(cycle, attemptPublicId);
    }

    @Transactional(readOnly = true)
    public PageResult<HistoryView> history(TenantContext tenant, String studentPublicId, int page, int size,
            AuthenticatedUser actor) {
        Scope scope = resolveStudentScope(tenant, studentPublicId, actor, true);
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        MapSqlParameterSource params = new MapSqlParameterSource("studentId", scope.student().getId())
                .addValue("offset", safePage * safeSize).addValue("size", safeSize);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM STUDENT_CERTIFICATION_HISTORY WHERE STUDENT_ID = :studentId",
                params, Long.class);
        List<HistoryView> content = jdbc.query("""
            SELECT PUBLIC_ID, EVENT_TYPE, PREVIOUS_VALUES, NEW_VALUES, REASON, CHANGED_AT
              FROM STUDENT_CERTIFICATION_HISTORY
             WHERE STUDENT_ID = :studentId
             ORDER BY CHANGED_AT DESC, STUDENT_CERTIFICATION_HISTORY_ID DESC
             OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY
            """, params, (rs, rowNum) -> new HistoryView(rs.getString("PUBLIC_ID"), rs.getString("EVENT_TYPE"),
                rs.getString("PREVIOUS_VALUES"), rs.getString("NEW_VALUES"), rs.getString("REASON"),
                instant(rs, "CHANGED_AT")));
        long safeTotal = total == null ? 0 : total;
        return new PageResult<>(content, safePage, safeSize, safeTotal,
                safeTotal == 0 ? 0 : (int) Math.ceil((double) safeTotal / safeSize));
    }

    private Catalogs catalogs(Long organizationId) {
        List<CatalogItem> profiles = catalog("CERTIFICATION_PROFILE_CATALOG", "PROFILE_CODE", "PROFILE_NAME",
                "SUGGESTED_TECH_PROFILE", "SORT_ORDER", organizationId);
        List<CatalogItem> technologies = catalog("QUESTION_TECHNOLOGY", "TECHNOLOGY_CODE", "TECHNOLOGY_NAME",
                "NULL", "DISPLAY_ORDER", organizationId);
        List<CatalogItem> technologicalProfiles = catalog("TECHNOLOGICAL_PROFILE_CATALOG", "PROFILE_CODE",
                "PROFILE_NAME", "NULL", "DISPLAY_ORDER", organizationId);
        return new Catalogs(profiles, technologies, technologicalProfiles,
                enumOptions(CertificationLevel.values()), enumOptions(CertificationTrackingStatus.values()),
                enumOptions(CertificationExamStatus.values()), enumOptions(CertificationType.values()));
    }

    private List<CatalogItem> catalog(String table, String code, String name, String suggested,
            String order, Long organizationId) {
        return jdbc.query("SELECT PUBLIC_ID, " + code + " CODE, " + name + " NAME, " + suggested
                + " SUGGESTED FROM " + table
                + " WHERE STATUS = 'ACTIVE' AND (CONTENT_SCOPE = 'GLOBAL' OR (CONTENT_SCOPE = 'ORGANIZATION'"
                + " AND OWNER_ORGANIZATION_ID = :organizationId)) ORDER BY " + order + ", " + name,
                Map.of("organizationId", organizationId), (rs, rowNum) -> new CatalogItem(rs.getString("PUBLIC_ID"),
                    rs.getString("CODE"), rs.getString("NAME"), rs.getString("SUGGESTED")));
    }

    private <E extends Enum<E>> List<EnumOption> enumOptions(E[] values) {
        List<EnumOption> result = new ArrayList<>();
        for (E value : values) result.add(new EnumOption(value.name(), label(value.name())));
        return result;
    }

    private String label(String value) {
        return switch (value) {
            case "TECHNOLOGICAL" -> "Tecnológica";
            case "DEVELOPMENT_SECURITY" -> "Desarrollo Seguro";
            case "NORMATIVE_TESTING" -> "Normativa y Testing";
            case "ONE" -> "ONE";
            case "AGILE" -> "Agile";
            case "CERTIFICATION" -> "Certificación";
            case "RECERTIFICATION" -> "Recertificación";
            case "NOT_SCHEDULED" -> "Sin programar";
            case "IN_PROGRESS" -> "En proceso";
            case "NOT_APPROVED" -> "No aprobada";
            case "NOT_OBTAINED" -> "Aún no obtenida";
            case "EXPIRING_SOON" -> "Próxima a vencer";
            default -> value.substring(0, 1) + value.substring(1).toLowerCase(Locale.ROOT).replace('_', ' ');
        };
    }

    private StudentCertificationDetail detail(Scope scope) {
        List<CycleView> cycles = cycles(scope);
        Applicability applicability = applicability(scope.student().getId());
        return new StudentCertificationDetail(studentSummary(scope), true, applicability,
                metrics(applicability, cycles), cycles);
    }

    private StudentSummary studentSummary(Scope scope) {
        List<StudentSummary> rows = jdbc.query("""
            SELECT s.PUBLIC_ID, s.DISPLAY_NAME, s.STATUS, s.ACCESS_VALID_FROM, s.ACCESS_EXPIRES_ON,
                   s.ADMISSION_DATE, o.PUBLIC_ID ORGANIZATION_PUBLIC_ID, o.ORGANIZATION_NAME,
                   p.PUBLIC_ID PROFILE_PUBLIC_ID, p.PROFILE_CODE, p.PROFILE_NAME,
                   tp.PUBLIC_ID TECH_PROFILE_PUBLIC_ID, tp.PROFILE_CODE TECH_PROFILE_CODE,
                   tp.PROFILE_NAME TECH_PROFILE_NAME
              FROM STUDENT s JOIN ORGANIZATION o ON o.ORGANIZATION_ID = s.ORGANIZATION_ID
              LEFT JOIN CERTIFICATION_PROFILE_CATALOG p ON p.CERTIFICATION_PROFILE_ID = s.PROFESSIONAL_PROFILE_ID
              LEFT JOIN TECHNOLOGICAL_PROFILE_CATALOG tp ON tp.TECHNOLOGICAL_PROFILE_ID = s.TECHNOLOGICAL_PROFILE_ID
             WHERE s.STUDENT_ID = :studentId
            """, Map.of("studentId", scope.student().getId()), (rs, rowNum) -> new StudentSummary(
                rs.getString("PUBLIC_ID"), rs.getString("DISPLAY_NAME"), rs.getString("ORGANIZATION_PUBLIC_ID"),
                rs.getString("ORGANIZATION_NAME"), rs.getString("STATUS"), localDate(rs, "ACCESS_VALID_FROM"),
                localDate(rs, "ACCESS_EXPIRES_ON"), localDate(rs, "ADMISSION_DATE"),
                catalogRef(rs, "PROFILE_PUBLIC_ID", "PROFILE_CODE", "PROFILE_NAME"),
                catalogRef(rs, "TECH_PROFILE_PUBLIC_ID", "TECH_PROFILE_CODE", "TECH_PROFILE_NAME")));
        return rows.getFirst();
    }

    private CatalogItem catalogRef(ResultSet rs, String publicId, String code, String name) throws SQLException {
        String id = rs.getString(publicId);
        return id == null ? null : new CatalogItem(id, rs.getString(code), rs.getString(name), null);
    }

    private Applicability applicability(Long studentId) {
        return jdbc.queryForObject("""
            SELECT APPLIES_TECH_CERT, APPLIES_DEV_SECURITY, APPLIES_NORMATIVE_TESTING, APPLIES_ONE, APPLIES_AGILE
              FROM STUDENT WHERE STUDENT_ID = :studentId
            """, Map.of("studentId", studentId), (rs, rowNum) -> new Applicability(rs.getBoolean(1),
                rs.getBoolean(2), rs.getBoolean(3), rs.getBoolean(4), rs.getBoolean(5)));
    }

    private List<CycleView> cycles(Scope scope) {
        return jdbc.query("""
            SELECT c.PUBLIC_ID, c.CERTIFICATION_TYPE, t.PUBLIC_ID TECHNOLOGY_PUBLIC_ID,
                   t.TECHNOLOGY_NAME, c.CERTIFICATION_LEVEL, c.IS_PRIMARY, c.PROCESS_TYPE,
                   c.TRACKING_STATUS, c.DEADLINE_DATE, c.SCHEDULED_DATE, c.APPLICATION_DATE,
                   c.APPROVED, c.EXPIRATION_DATE, c.VALIDITY_STATUS,
                   previous.PUBLIC_ID PREVIOUS_PUBLIC_ID, c.ACTIONS_TO_TAKE, c.SOFTTEK_MANAGEMENT,
                   c.OBSERVATIONS, c.ACTIVE, c.VERSION_NO,
                   (SELECT MAX(a.SCORE) KEEP (DENSE_RANK LAST ORDER BY a.ATTEMPT_NUMBER)
                      FROM STUDENT_CERTIFICATION_CYCLE_ATTEMPT a
                     WHERE a.STUDENT_CERTIFICATION_CYCLE_ID = c.STUDENT_CERTIFICATION_CYCLE_ID) LATEST_SCORE,
                   (SELECT COUNT(*) FROM STUDENT_CERTIFICATION_CYCLE_ATTEMPT a
                     WHERE a.STUDENT_CERTIFICATION_CYCLE_ID = c.STUDENT_CERTIFICATION_CYCLE_ID) ATTEMPT_COUNT
              FROM STUDENT_CERTIFICATION_CYCLE c
              LEFT JOIN QUESTION_TECHNOLOGY t ON t.TECHNOLOGY_ID = c.TECHNOLOGY_ID
              LEFT JOIN STUDENT_CERTIFICATION_CYCLE previous
                ON previous.STUDENT_CERTIFICATION_CYCLE_ID = c.PREVIOUS_APPROVED_CYCLE_ID
             WHERE c.STUDENT_ID = :studentId AND c.ORGANIZATION_ID = :organizationId
             ORDER BY c.CERTIFICATION_TYPE, c.IS_PRIMARY DESC, c.CREATED_AT DESC
            """, Map.of("studentId", scope.student().getId(), "organizationId", scope.organizationId()),
                (rs, rowNum) -> mapCycle(rs));
    }

    private CycleView cycle(Scope scope, String publicId) {
        return cycles(scope).stream().filter(item -> item.publicId().equals(publicId)).findFirst()
                .orElseThrow(() -> new BusinessException("CERTIFICATION_CYCLE_NOT_FOUND", "El ciclo no existe."));
    }

    private CycleView mapCycle(ResultSet rs) throws SQLException {
        BigDecimal latest = rs.getBigDecimal("LATEST_SCORE");
        return new CycleView(rs.getString("PUBLIC_ID"), CertificationType.valueOf(rs.getString("CERTIFICATION_TYPE")),
                rs.getString("TECHNOLOGY_PUBLIC_ID"), rs.getString("TECHNOLOGY_NAME"),
                nullableEnum(CertificationLevel.class, rs.getString("CERTIFICATION_LEVEL")),
                rs.getBoolean("IS_PRIMARY"), CertificationProcessType.valueOf(rs.getString("PROCESS_TYPE")),
                CertificationTrackingStatus.valueOf(rs.getString("TRACKING_STATUS")),
                localDate(rs, "DEADLINE_DATE"), localDate(rs, "SCHEDULED_DATE"),
                localDate(rs, "APPLICATION_DATE"), nullableBoolean(rs, "APPROVED"),
                localDate(rs, "EXPIRATION_DATE"), CertificationValidityStatus.valueOf(rs.getString("VALIDITY_STATUS")),
                rs.getString("PREVIOUS_PUBLIC_ID"), rs.getString("ACTIONS_TO_TAKE"),
                rs.getString("SOFTTEK_MANAGEMENT"), rs.getString("OBSERVATIONS"), rs.getBoolean("ACTIVE"),
                latest, rs.getInt("ATTEMPT_COUNT"), rs.getLong("VERSION_NO"));
    }

    private Metrics metrics(Applicability applicability, List<CycleView> cycles) {
        int applicable = (applicability.technological() ? 1 : 0) + (applicability.developmentSecurity() ? 1 : 0)
                + (applicability.normativeTesting() ? 1 : 0) + (applicability.one() ? 1 : 0)
                + (applicability.agile() ? 1 : 0);
        return new Metrics(applicable,
                (int) cycles.stream().filter(c -> c.active() && (c.trackingStatus() == CertificationTrackingStatus.PENDING
                        || c.trackingStatus() == CertificationTrackingStatus.NOT_SCHEDULED)).count(),
                (int) cycles.stream().filter(c -> c.active() && c.trackingStatus() == CertificationTrackingStatus.SCHEDULED).count(),
                (int) cycles.stream().filter(c -> Boolean.TRUE.equals(c.approved())).count(),
                (int) cycles.stream().filter(c -> Boolean.FALSE.equals(c.approved())).count(),
                (int) cycles.stream().filter(c -> c.validityStatus() == CertificationValidityStatus.VALID).count(),
                (int) cycles.stream().filter(c -> c.validityStatus() == CertificationValidityStatus.EXPIRING_SOON).count(),
                (int) cycles.stream().filter(c -> c.validityStatus() == CertificationValidityStatus.EXPIRED).count(),
                (int) cycles.stream().filter(c -> c.processType() == CertificationProcessType.RECERTIFICATION
                        && c.active() && !Boolean.TRUE.equals(c.approved())).count());
    }

    private void updateApplicability(Scope scope, Applicability flags, Long actorId) {
        jdbc.update("""
            UPDATE STUDENT
               SET APPLIES_TECH_CERT = :technological,
                   APPLIES_DEV_SECURITY = :developmentSecurity,
                   APPLIES_NORMATIVE_TESTING = :normativeTesting,
                   APPLIES_ONE = :one,
                   APPLIES_AGILE = :agile,
                   CERTIFICATIONS_ENABLED = CASE WHEN :sum > 0 THEN 1 ELSE 0 END,
                   UPDATED_BY = :actorId, UPDATED_AT = SYSTIMESTAMP
             WHERE STUDENT_ID = :studentId AND ORGANIZATION_ID = :organizationId
            """, new MapSqlParameterSource()
                .addValue("technological", flags.technological() ? 1 : 0)
                .addValue("developmentSecurity", flags.developmentSecurity() ? 1 : 0)
                .addValue("normativeTesting", flags.normativeTesting() ? 1 : 0)
                .addValue("one", flags.one() ? 1 : 0)
                .addValue("agile", flags.agile() ? 1 : 0)
                .addValue("sum", (flags.technological() ? 1 : 0) + (flags.developmentSecurity() ? 1 : 0)
                        + (flags.normativeTesting() ? 1 : 0) + (flags.one() ? 1 : 0) + (flags.agile() ? 1 : 0))
                .addValue("actorId", actorId).addValue("studentId", scope.student().getId())
                .addValue("organizationId", scope.organizationId()));
        Map<CertificationType, Boolean> byType = new EnumMap<>(CertificationType.class);
        byType.put(CertificationType.TECHNOLOGICAL, flags.technological());
        byType.put(CertificationType.DEVELOPMENT_SECURITY, flags.developmentSecurity());
        byType.put(CertificationType.NORMATIVE_TESTING, flags.normativeTesting());
        byType.put(CertificationType.ONE, flags.one());
        byType.put(CertificationType.AGILE, flags.agile());
        byType.forEach((type, active) -> jdbc.update("""
            UPDATE STUDENT_CERTIFICATION_CYCLE
               SET ACTIVE = CASE
                       WHEN :active = 0 THEN 0
                       WHEN TRACKING_STATUS <> 'CANCELLED' THEN 1
                       ELSE ACTIVE
                   END,
                   UPDATED_BY = :actorId,
                   UPDATED_AT = SYSTIMESTAMP
             WHERE STUDENT_ID = :studentId AND CERTIFICATION_TYPE = :type
            """, Map.of("active", active ? 1 : 0, "actorId", actorId,
                "studentId", scope.student().getId(), "type", type.name())));
        history(scope, null, null, "APPLICABILITY_CHANGED", null, flags.toString(), null, actorId);
    }

    private String createCycle(Scope scope, CycleCommand command, Long actorId) {
        validateCycle(scope, command, null);
        CertificationProcessType process = processType(scope, command);
        Long previous = previousApprovedCycleId(scope, command);
        LocalDate deadline = deadline(scope, command.type());
        Result result = calculateResult(command.type(), command.applicationDate(), command.approved(),
                command.trackingStatus(), scope.organizationId());
        Long technologyId = resolveTechnology(scope.organizationId(), command.type(), command.technologyPublicId());
        if (command.type() == CertificationType.TECHNOLOGICAL && command.primary()) {
            jdbc.update("""
                UPDATE STUDENT_CERTIFICATION_CYCLE
                   SET IS_PRIMARY = 0, UPDATED_BY = :actorId, UPDATED_AT = SYSTIMESTAMP
                 WHERE STUDENT_ID = :studentId AND CERTIFICATION_TYPE = 'TECHNOLOGICAL'
                """, Map.of("actorId", actorId, "studentId", scope.student().getId()));
        }
        String publicId = UUID.randomUUID().toString();
        MapSqlParameterSource params = cycleParams(scope, publicId, command, technologyId, process, previous,
                deadline, result, actorId);
        try {
            jdbc.update("""
                INSERT INTO STUDENT_CERTIFICATION_CYCLE (
                    PUBLIC_ID, STUDENT_ID, ORGANIZATION_ID, CERTIFICATION_TYPE, TECHNOLOGY_ID,
                    CERTIFICATION_LEVEL, IS_PRIMARY, PROCESS_TYPE, TRACKING_STATUS, DEADLINE_DATE,
                    SCHEDULED_DATE, APPLICATION_DATE, APPROVED, EXPIRATION_DATE, VALIDITY_STATUS,
                    PREVIOUS_APPROVED_CYCLE_ID, ACTIONS_TO_TAKE, SOFTTEK_MANAGEMENT, OBSERVATIONS,
                    ACTIVE, CREATED_BY, UPDATED_BY, CREATED_AT, UPDATED_AT, VERSION_NO
                ) VALUES (
                    :publicId, :studentId, :organizationId, :type, :technologyId,
                    :level, :primary, :processType, :trackingStatus, :deadlineDate,
                    :scheduledDate, :applicationDate, :approved, :expirationDate, :validityStatus,
                    :previousId, :actionsToTake, :softtekManagement, :observations,
                    :active, :actorId, :actorId, SYSTIMESTAMP, SYSTIMESTAMP, 0
                )
                """, params);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException("CERTIFICATION_CYCLE_CONFLICT",
                    command.primary() ? "Ya existe una certificación tecnológica principal activa."
                            : "No fue posible crear el ciclo porque su configuración entra en conflicto.");
        }
        Long cycleId = jdbc.queryForObject("SELECT STUDENT_CERTIFICATION_CYCLE_ID FROM STUDENT_CERTIFICATION_CYCLE WHERE PUBLIC_ID = :publicId",
                Map.of("publicId", publicId), Long.class);
        history(scope, cycleId, null, "CYCLE_CREATED", null,
                "type=" + command.type() + ";process=" + process, null, actorId);
        return publicId;
    }

    private void updateCycle(Scope scope, String publicId, CycleCommand command, Long actorId) {
        CycleRow existing = requireCycle(scope, publicId);
        validateCycle(scope, command, existing);
        if (command.version() == null || !Objects.equals(command.version(), existing.version())) {
            throw new BusinessException("CERTIFICATION_CYCLE_VERSION_CONFLICT",
                    "El ciclo fue modificado por otra sesión. Actualiza la página.");
        }
        Long technologyId = resolveTechnology(scope.organizationId(), command.type(), command.technologyPublicId());
        Result result = calculateResult(command.type(), command.applicationDate(), command.approved(),
                command.trackingStatus(), scope.organizationId());
        if (command.type() == CertificationType.TECHNOLOGICAL && command.primary()) {
            jdbc.update("""
                UPDATE STUDENT_CERTIFICATION_CYCLE
                   SET IS_PRIMARY = 0, UPDATED_BY = :actorId, UPDATED_AT = SYSTIMESTAMP
                 WHERE STUDENT_ID = :studentId AND CERTIFICATION_TYPE = 'TECHNOLOGICAL'
                   AND STUDENT_CERTIFICATION_CYCLE_ID <> :cycleId
                """, Map.of("actorId", actorId, "studentId", scope.student().getId(),
                    "cycleId", existing.id()));
        }
        MapSqlParameterSource params = cycleParams(scope, publicId, command, technologyId, existing.processType(),
                existing.previousApprovedId(), existing.deadlineDate(), result, actorId)
                .addValue("id", existing.id()).addValue("version", existing.version());
        int updated;
        try {
            updated = jdbc.update("""
            UPDATE STUDENT_CERTIFICATION_CYCLE
               SET TECHNOLOGY_ID = :technologyId, CERTIFICATION_LEVEL = :level,
                   IS_PRIMARY = :primary, TRACKING_STATUS = :trackingStatus,
                   SCHEDULED_DATE = :scheduledDate, APPLICATION_DATE = :applicationDate,
                   APPROVED = :approved, EXPIRATION_DATE = :expirationDate,
                   VALIDITY_STATUS = :validityStatus, ACTIONS_TO_TAKE = :actionsToTake,
                   SOFTTEK_MANAGEMENT = :softtekManagement, OBSERVATIONS = :observations,
                   ACTIVE = :active, UPDATED_BY = :actorId, UPDATED_AT = SYSTIMESTAMP,
                   VERSION_NO = VERSION_NO + 1
             WHERE STUDENT_CERTIFICATION_CYCLE_ID = :id AND VERSION_NO = :version
            """, params);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException("CERTIFICATION_CYCLE_CONFLICT",
                    command.primary() ? "Ya existe una certificación tecnológica principal activa."
                            : "No fue posible actualizar el ciclo porque su configuración entra en conflicto.");
        }
        if (updated != 1) throw new BusinessException("CERTIFICATION_CYCLE_VERSION_CONFLICT",
                "El ciclo fue modificado por otra sesión. Actualiza la página.");
        history(scope, existing.id(), null, "CYCLE_UPDATED", existing.toString(), command.toString(), null, actorId);
    }

    private MapSqlParameterSource cycleParams(Scope scope, String publicId, CycleCommand command, Long technologyId,
            CertificationProcessType process, Long previous, LocalDate deadline, Result result, Long actorId) {
        return new MapSqlParameterSource()
                .addValue("publicId", publicId).addValue("studentId", scope.student().getId())
                .addValue("organizationId", scope.organizationId()).addValue("type", command.type().name())
                .addValue("technologyId", technologyId)
                .addValue("level", command.certificationLevel() == null ? null : command.certificationLevel().name())
                .addValue("primary", command.type() == CertificationType.TECHNOLOGICAL && command.primary() ? 1 : 0)
                .addValue("processType", process.name())
                .addValue("trackingStatus", result.trackingStatus().name())
                .addValue("deadlineDate", date(deadline)).addValue("scheduledDate", date(command.scheduledDate()))
                .addValue("applicationDate", date(command.applicationDate()))
                .addValue("approved", command.approved() == null ? null : command.approved() ? 1 : 0)
                .addValue("expirationDate", date(result.expirationDate()))
                .addValue("validityStatus", result.validityStatus().name()).addValue("previousId", previous)
                .addValue("actionsToTake", clean(command.actionsToTake(), 1000))
                .addValue("softtekManagement", clean(command.softtekManagement(), 1000))
                .addValue("observations", clean(command.observations(), 1000))
                .addValue("active", command.active() ? 1 : 0).addValue("actorId", actorId);
    }

    private void validateCycle(Scope scope, CycleCommand command, CycleRow existing) {
        if (command == null || command.type() == null) {
            throw new BusinessException("CERTIFICATION_TYPE_REQUIRED", "Selecciona el tipo de certificación.");
        }
        if (existing != null && existing.type() != command.type()) {
            throw new BusinessException("CERTIFICATION_TYPE_IMMUTABLE", "El tipo del ciclo no puede cambiarse.");
        }
        Applicability flags = applicability(scope.student().getId());
        boolean applies = switch (command.type()) {
            case TECHNOLOGICAL -> flags.technological();
            case DEVELOPMENT_SECURITY -> flags.developmentSecurity();
            case NORMATIVE_TESTING -> flags.normativeTesting();
            case ONE -> flags.one();
            case AGILE -> flags.agile();
        };
        if (!applies) throw new BusinessException("CERTIFICATION_AREA_NOT_APPLICABLE",
                "Activa primero el seguimiento correspondiente para este estudiante.");
        if (command.type() == CertificationType.TECHNOLOGICAL) {
            if (command.technologyPublicId() == null || command.technologyPublicId().isBlank()) {
                throw new BusinessException("CERTIFICATION_TECHNOLOGY_REQUIRED", "Selecciona una tecnología.");
            }
            if (command.certificationLevel() == null) {
                throw new BusinessException("CERTIFICATION_LEVEL_REQUIRED", "Selecciona el nivel JR, STD o SR.");
            }
        } else if (command.technologyPublicId() != null || command.certificationLevel() != null || command.primary()) {
            throw new BusinessException("CERTIFICATION_TECHNOLOGY_FIELDS_INVALID",
                    "Tecnología, nivel y principal solo aplican a certificaciones tecnológicas.");
        }
        if (Boolean.TRUE.equals(command.approved()) && command.applicationDate() == null) {
            throw new BusinessException("CERTIFICATION_APPLICATION_DATE_REQUIRED",
                    "La fecha de aplicación es obligatoria para aprobar una certificación.");
        }
    }

    private CertificationProcessType processType(Scope scope, CycleCommand command) {
        if (command.type() == CertificationType.ONE || command.type() == CertificationType.AGILE) {
            return CertificationProcessType.CERTIFICATION;
        }
        return previousApprovedCycleId(scope, command) == null
                ? CertificationProcessType.CERTIFICATION : CertificationProcessType.RECERTIFICATION;
    }

    private Long previousApprovedCycleId(Scope scope, CycleCommand command) {
        StringBuilder sql = new StringBuilder("""
            SELECT STUDENT_CERTIFICATION_CYCLE_ID
              FROM STUDENT_CERTIFICATION_CYCLE
             WHERE STUDENT_ID = :studentId AND ORGANIZATION_ID = :organizationId
               AND CERTIFICATION_TYPE = :type AND APPROVED = 1
            """);
        MapSqlParameterSource params = new MapSqlParameterSource("studentId", scope.student().getId())
                .addValue("organizationId", scope.organizationId()).addValue("type", command.type().name());
        if (command.type() == CertificationType.TECHNOLOGICAL) {
            Long technologyId = resolveTechnology(scope.organizationId(), command.type(), command.technologyPublicId());
            sql.append(" AND TECHNOLOGY_ID = :technologyId AND CERTIFICATION_LEVEL = :level ");
            params.addValue("technologyId", technologyId).addValue("level", command.certificationLevel().name());
        }
        sql.append(" ORDER BY APPLICATION_DATE DESC NULLS LAST, STUDENT_CERTIFICATION_CYCLE_ID DESC FETCH FIRST 1 ROWS ONLY");
        List<Long> rows = jdbc.query(sql.toString(), params, (rs, rowNum) -> rs.getLong(1));
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private LocalDate deadline(Scope scope, CertificationType type) {
        LocalDate admission = scope.student().getAdmissionDate();
        if (admission == null) throw new BusinessException("CERTIFICATION_ADMISSION_DATE_REQUIRED",
                "La fecha de alta es obligatoria para calcular las fechas límite.");
        Policy policy = policy(scope.organizationId(), type);
        if (policy.deadlineMonths() == null && policy.deadlineDays() == null) return null;
        return CertificationLifecycleCalculator.deadline(admission, policy.deadlineMonths(), policy.deadlineDays());
    }

    private Result calculateResult(CertificationType type, LocalDate applicationDate, Boolean approved,
            CertificationTrackingStatus requestedStatus, Long organizationId) {
        CertificationTrackingStatus tracking = requestedStatus == null
                ? CertificationTrackingStatus.PENDING : requestedStatus;
        if (Boolean.TRUE.equals(approved)) tracking = CertificationTrackingStatus.APPROVED;
        else if (Boolean.FALSE.equals(approved) && applicationDate != null) tracking = CertificationTrackingStatus.NOT_APPROVED;
        if (type == CertificationType.ONE || type == CertificationType.AGILE) {
            return new Result(null, CertificationValidityStatus.NOT_OBTAINED, tracking);
        }
        if (!Boolean.TRUE.equals(approved)) {
            return new Result(null, CertificationValidityStatus.NOT_OBTAINED, tracking);
        }
        Policy policy = policy(organizationId, type);
        int years = policy.validityYears() == null ? 2 : policy.validityYears();
        LocalDate expiration = CertificationLifecycleCalculator.expiration(applicationDate, years);
        return new Result(expiration, validity(expiration), tracking);
    }

    private CertificationValidityStatus validity(LocalDate expiration) {
        return CertificationLifecycleCalculator.validity(expiration, LocalDate.now(clock), 90);
    }

    private Long resolveTechnology(Long organizationId, CertificationType type, String publicId) {
        if (type != CertificationType.TECHNOLOGICAL) return null;
        List<Long> rows = jdbc.query("""
            SELECT TECHNOLOGY_ID FROM QUESTION_TECHNOLOGY
             WHERE PUBLIC_ID = :publicId AND STATUS = 'ACTIVE'
               AND (CONTENT_SCOPE = 'GLOBAL' OR (CONTENT_SCOPE = 'ORGANIZATION' AND OWNER_ORGANIZATION_ID = :organizationId))
            """, Map.of("publicId", publicId, "organizationId", organizationId), (rs, rowNum) -> rs.getLong(1));
        if (rows.isEmpty()) throw new BusinessException("CERTIFICATION_TECHNOLOGY_INVALID",
                "La tecnología seleccionada no pertenece al contexto autorizado.");
        return rows.getFirst();
    }

    private void validateAttemptsSupported(CycleRow cycle) {
        if (cycle.type() == CertificationType.ONE || cycle.type() == CertificationType.AGILE) {
            throw new BusinessException("CERTIFICATION_ATTEMPTS_NOT_SUPPORTED",
                    "ONE y Agile no administran intentos ni examen.");
        }
    }

    private void validateAttempt(AttemptCommand command) {
        if (command == null) throw new BusinessException("CERTIFICATION_ATTEMPT_REQUIRED", "Captura el intento.");
        if (command.score() != null && (command.score().compareTo(BigDecimal.ZERO) < 0
                || command.score().compareTo(new BigDecimal("100")) > 0)) {
            throw new BusinessException("CERTIFICATION_SCORE_INVALID", "El promedio debe estar entre 0 y 100.");
        }
        if (Boolean.TRUE.equals(command.approved()) && command.applicationDate() == null) {
            throw new BusinessException("CERTIFICATION_ATTEMPT_DATE_REQUIRED",
                    "La fecha de aplicación es obligatoria para aprobar el intento.");
        }
    }

    private MapSqlParameterSource attemptParams(Scope scope, CycleRow cycle, String publicId, int attemptNumber,
            AttemptCommand command, Long actorId) {
        CertificationExamStatus exam = command.examStatus() == null ? CertificationExamStatus.NOT_SCHEDULED : command.examStatus();
        return new MapSqlParameterSource("publicId", publicId).addValue("cycleId", cycle.id())
                .addValue("organizationId", scope.organizationId()).addValue("attemptNumber", attemptNumber)
                .addValue("scheduledDate", date(command.scheduledDate())).addValue("applicationDate", date(command.applicationDate()))
                .addValue("examStatus", exam.name()).addValue("score", command.score())
                .addValue("approved", command.approved() == null ? null : command.approved() ? 1 : 0)
                .addValue("result", clean(command.result(), 1000)).addValue("observations", clean(command.observations(), 1000))
                .addValue("actorId", actorId);
    }

    private void applyAttemptResult(Scope scope, CycleRow cycle, AttemptCommand command, Long actorId) {
        if (command.applicationDate() == null && command.approved() == null) return;
        Result result = calculateResult(cycle.type(), command.applicationDate(), command.approved(),
                command.approved() == null ? cycle.trackingStatus()
                        : command.approved() ? CertificationTrackingStatus.APPROVED : CertificationTrackingStatus.NOT_APPROVED,
                scope.organizationId());
        LocalDate nextScheduled = null;
        if (Boolean.FALSE.equals(command.approved()) && command.applicationDate() != null) {
            Policy policy = policy(scope.organizationId(), cycle.type());
            nextScheduled = CertificationLifecycleCalculator.nextAttempt(command.applicationDate(),
                    policy.retryMonths(), policy.retryDays());
        }
        jdbc.update("""
            UPDATE STUDENT_CERTIFICATION_CYCLE
               SET APPLICATION_DATE = COALESCE(:applicationDate, APPLICATION_DATE),
                   APPROVED = :approved, TRACKING_STATUS = :trackingStatus,
                   EXPIRATION_DATE = :expirationDate, VALIDITY_STATUS = :validityStatus,
                   SCHEDULED_DATE = COALESCE(:nextScheduled, SCHEDULED_DATE),
                   UPDATED_BY = :actorId, UPDATED_AT = SYSTIMESTAMP, VERSION_NO = VERSION_NO + 1
             WHERE STUDENT_CERTIFICATION_CYCLE_ID = :cycleId
            """, new MapSqlParameterSource("applicationDate", date(command.applicationDate()))
                .addValue("approved", command.approved() == null ? null : command.approved() ? 1 : 0)
                .addValue("trackingStatus", result.trackingStatus().name())
                .addValue("expirationDate", date(result.expirationDate()))
                .addValue("validityStatus", result.validityStatus().name())
                .addValue("nextScheduled", date(nextScheduled)).addValue("actorId", actorId)
                .addValue("cycleId", cycle.id()));
    }

    private AttemptView queryAttempt(CycleRow cycle, String attemptPublicId) {
        List<AttemptView> rows = jdbc.query("""
            SELECT PUBLIC_ID, ATTEMPT_NUMBER, SCHEDULED_DATE, APPLICATION_DATE, EXAM_STATUS,
                   SCORE, APPROVED, RESULT, OBSERVATIONS, CREATED_AT, VERSION_NO
              FROM STUDENT_CERTIFICATION_CYCLE_ATTEMPT
             WHERE PUBLIC_ID = :publicId AND STUDENT_CERTIFICATION_CYCLE_ID = :cycleId
            """, Map.of("publicId", attemptPublicId, "cycleId", cycle.id()),
                (rs, rowNum) -> mapAttempt(rs, cycle.publicId()));
        if (rows.isEmpty()) throw new BusinessException("CERTIFICATION_ATTEMPT_NOT_FOUND", "El intento no existe.");
        return rows.getFirst();
    }

    private AttemptView mapAttempt(ResultSet rs, String cyclePublicId) throws SQLException {
        return new AttemptView(rs.getString("PUBLIC_ID"), cyclePublicId, rs.getInt("ATTEMPT_NUMBER"),
                localDate(rs, "SCHEDULED_DATE"), localDate(rs, "APPLICATION_DATE"),
                CertificationExamStatus.valueOf(rs.getString("EXAM_STATUS")), rs.getBigDecimal("SCORE"),
                nullableBoolean(rs, "APPROVED"), rs.getString("RESULT"), rs.getString("OBSERVATIONS"),
                instant(rs, "CREATED_AT"), rs.getLong("VERSION_NO"));
    }

    private CycleRow requireCycle(Scope scope, String publicId) {
        List<CycleRow> rows = jdbc.query("""
            SELECT STUDENT_CERTIFICATION_CYCLE_ID, PUBLIC_ID, CERTIFICATION_TYPE, PROCESS_TYPE,
                   TRACKING_STATUS, DEADLINE_DATE, PREVIOUS_APPROVED_CYCLE_ID, ACTIVE, VERSION_NO
              FROM STUDENT_CERTIFICATION_CYCLE
             WHERE PUBLIC_ID = :publicId AND STUDENT_ID = :studentId AND ORGANIZATION_ID = :organizationId
            """, Map.of("publicId", publicId, "studentId", scope.student().getId(),
                "organizationId", scope.organizationId()), (rs, rowNum) -> new CycleRow(rs.getLong(1),
                    rs.getString(2), CertificationType.valueOf(rs.getString(3)),
                    CertificationProcessType.valueOf(rs.getString(4)),
                    CertificationTrackingStatus.valueOf(rs.getString(5)), localDate(rs, "DEADLINE_DATE"),
                    nullableLong(rs, "PREVIOUS_APPROVED_CYCLE_ID"), rs.getBoolean("ACTIVE"), rs.getLong("VERSION_NO")));
        if (rows.isEmpty()) throw new BusinessException("CERTIFICATION_CYCLE_NOT_FOUND", "El ciclo no existe.");
        return rows.getFirst();
    }

    private Scope resolveStudentScope(TenantContext tenant, String studentPublicId, AuthenticatedUser actor,
            boolean requireCertifications) {
        if (!hasOperationalRole(actor)) throw new BusinessException("CERTIFICATION_ACCESS_FORBIDDEN",
                "No tienes permisos para administrar certificaciones.");
        StudentJpaEntity student;
        if (tenant != null && tenant.globalAdministrator() && actor.roles().contains("ADMINISTRATOR")) {
            student = students.findByPublicId(studentPublicId)
                    .filter(item -> item.getStatus() != StudentStatus.DELETED)
                    .orElseThrow(() -> new BusinessException("STUDENT_NOT_FOUND", "El estudiante no existe."));
        } else {
            if (tenant == null || !tenant.hasOrganization()) throw new BusinessException("ORGANIZATION_CONTEXT_REQUIRED",
                    "No existe un contexto organizacional autorizado.");
            student = students.findByOrganizationIdAndPublicId(tenant.organizationId(), studentPublicId)
                    .filter(item -> item.getStatus() != StudentStatus.DELETED)
                    .orElseThrow(() -> new BusinessException("STUDENT_NOT_FOUND", "El estudiante no existe."));
        }
        OrganizationJpaEntity organization = organizations.findById(student.getOrganizationId())
                .orElseThrow(() -> new BusinessException("ORGANIZATION_NOT_FOUND", "La organización no existe."));
        if (organization.getOrganizationType() != OrganizationType.CUSTOMER) {
            throw new BusinessException("CERTIFICATION_ACCESS_FORBIDDEN", "GLOBAL no administra estudiantes comerciales.");
        }
        if (requireCertifications && !organization.isAppliesCertifications()) {
            throw new BusinessException("CERTIFICATIONS_NOT_ENABLED",
                    "La organización seleccionada no tiene habilitada la gestión de certificaciones.");
        }
        return new Scope(student, organization.getId(), organization);
    }

    private Scope requireTenantOrganization(TenantContext tenant, AuthenticatedUser actor) {
        if (!hasOperationalRole(actor) || tenant == null || !tenant.hasOrganization()) {
            throw new BusinessException("ORGANIZATION_CONTEXT_REQUIRED", "Selecciona una organización autorizada.");
        }
        OrganizationJpaEntity organization = organizations.findById(tenant.organizationId())
                .orElseThrow(() -> new BusinessException("ORGANIZATION_NOT_FOUND", "La organización no existe."));
        if (!organization.isAppliesCertifications()) throw new BusinessException("CERTIFICATIONS_NOT_ENABLED",
                "La organización seleccionada no tiene habilitada la gestión de certificaciones.");
        return new Scope(null, organization.getId(), organization);
    }

    private boolean hasOperationalRole(AuthenticatedUser actor) {
        return actor != null && (actor.roles().contains("ADMINISTRATOR") || actor.roles().contains("MANAGER")
                || actor.roles().contains("SUPERVISOR"));
    }

    private Policy policy(Long organizationId, CertificationType type) {
        List<Policy> rows = jdbc.query("""
            SELECT DEADLINE_MONTHS, DEADLINE_DAYS, RETRY_MONTHS, RETRY_DAYS, VALIDITY_YEARS
              FROM ORGANIZATION_CERTIFICATION_POLICY
             WHERE ORGANIZATION_ID = :organizationId AND CERTIFICATION_TYPE = :type AND STATUS = 'ACTIVE'
            """, Map.of("organizationId", organizationId, "type", type.name()), (rs, rowNum) -> new Policy(
                nullableInteger(rs, "DEADLINE_MONTHS"), nullableInteger(rs, "DEADLINE_DAYS"),
                nullableInteger(rs, "RETRY_MONTHS"), nullableInteger(rs, "RETRY_DAYS"),
                nullableInteger(rs, "VALIDITY_YEARS")));
        if (!rows.isEmpty()) return rows.getFirst();
        return switch (type) {
            case TECHNOLOGICAL -> new Policy(1, 0, 0, 15, 2);
            case DEVELOPMENT_SECURITY -> new Policy(3, 0, 1, 15, 2);
            case NORMATIVE_TESTING -> new Policy(2, 0, 1, 0, 2);
            case AGILE -> new Policy(3, 0, 1, 15, null);
            case ONE -> new Policy(null, null, null, null, null);
        };
    }

    private void history(Scope scope, Long cycleId, Long attemptId, String event, String previous,
            String next, String reason, Long actorId) {
        jdbc.update("""
            INSERT INTO STUDENT_CERTIFICATION_HISTORY (
                PUBLIC_ID, STUDENT_ID, ORGANIZATION_ID, STUDENT_CERT_REQUIREMENT_ID,
                EVENT_TYPE, PREVIOUS_VALUES, NEW_VALUES, REASON, CHANGED_BY, CHANGED_AT,
                STUDENT_CERTIFICATION_CYCLE_ID, STUDENT_CERTIFICATION_CYCLE_ATTEMPT_ID
            ) VALUES (
                :publicId, :studentId, :organizationId, NULL,
                :eventType, :previousValues, :newValues, :reason, :actorId, SYSTIMESTAMP,
                :cycleId, :attemptId
            )
            """, new MapSqlParameterSource("publicId", UUID.randomUUID().toString())
                .addValue("studentId", scope.student().getId()).addValue("organizationId", scope.organizationId())
                .addValue("eventType", event).addValue("previousValues", previous).addValue("newValues", next)
                .addValue("reason", reason).addValue("actorId", actorId).addValue("cycleId", cycleId)
                .addValue("attemptId", attemptId));
    }

    private void audit(Long actorId, String event, Scope scope, String cyclePublicId, String description,
            String ipAddress, String userAgent) {
        Map<String, Object> data = new HashMap<>();
        data.put("studentPublicId", scope.student().getPublicId());
        data.put("organizationId", scope.organizationId());
        if (cyclePublicId != null) data.put("cyclePublicId", cyclePublicId);
        audit.record(actorId, event, "STUDENT_CERTIFICATIONS", description, ipAddress, userAgent, data, clock.instant());
    }

    private static java.sql.Date date(LocalDate value) { return value == null ? null : java.sql.Date.valueOf(value); }
    private static int zero(Integer value) { return value == null ? 0 : value; }
    private static String clean(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String cleaned = value.replaceAll("<[^>]*>", "").trim();
        return cleaned.length() <= max ? cleaned : cleaned.substring(0, max);
    }
    private static LocalDate localDate(ResultSet rs, String column) throws SQLException {
        java.sql.Date value = rs.getDate(column); return value == null ? null : value.toLocalDate();
    }
    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column); return value == null ? null : value.toInstant();
    }
    private static Boolean nullableBoolean(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column); return rs.wasNull() ? null : value == 1;
    }
    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column); return rs.wasNull() ? null : value;
    }
    private static Integer nullableInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column); return rs.wasNull() ? null : value;
    }
    private static <E extends Enum<E>> E nullableEnum(Class<E> type, String value) {
        return value == null ? null : Enum.valueOf(type, value);
    }

    private record Scope(StudentJpaEntity student, Long organizationId, OrganizationJpaEntity organization) {}
    private record Policy(Integer deadlineMonths, Integer deadlineDays, Integer retryMonths,
            Integer retryDays, Integer validityYears) {}
    private record Result(LocalDate expirationDate, CertificationValidityStatus validityStatus,
            CertificationTrackingStatus trackingStatus) {}
    private record CycleRow(Long id, String publicId, CertificationType type, CertificationProcessType processType,
            CertificationTrackingStatus trackingStatus, LocalDate deadlineDate, Long previousApprovedId,
            boolean active, Long version) {}
}
