package com.nexoskill.evaluation.questionbank.application.service;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.organizations.domain.model.TenantContext;
import com.nexoskill.evaluation.questionbank.domain.model.QuestionAvailabilityMode;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import com.nexoskill.evaluation.shared.domain.PublicIdNormalizer;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuestionAvailabilityService {
    private static final String GLOBAL_SCOPE = "GLOBAL";

    private final NamedParameterJdbcTemplate jdbc;
    private final AuditLogPort audit;
    private final Clock clock;

    public QuestionAvailabilityService(NamedParameterJdbcTemplate jdbc, AuditLogPort audit, Clock clock) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AvailabilityView get(String questionPublicId, TenantContext tenant) {
        QuestionRow question = findVisibleQuestion(questionPublicId, tenant);
        if (!GLOBAL_SCOPE.equals(question.scope())) {
            return new AvailabilityView(QuestionAvailabilityMode.NONE, List.of());
        }

        List<OrganizationView> organizations = List.of();
        if (question.mode() == QuestionAvailabilityMode.SELECTED_ORGANIZATIONS) {
            String sql = """
                SELECT o.PUBLIC_ID, o.ORGANIZATION_CODE, o.ORGANIZATION_NAME
                  FROM QUESTION_ORGANIZATION_AVAILABILITY availability
                  JOIN ORGANIZATION o ON o.ORGANIZATION_ID = availability.ORGANIZATION_ID
                 WHERE availability.QUESTION_ID = :questionId
                   AND availability.STATUS = 'ACTIVE'
                """ + (tenant.globalAdministrator() ? "" : " AND o.ORGANIZATION_ID = :tenantOrganizationId ")
                    + " ORDER BY o.ORGANIZATION_NAME ";
            MapSqlParameterSource params = new MapSqlParameterSource("questionId", question.id());
            if (!tenant.globalAdministrator()) params.addValue("tenantOrganizationId", tenant.organizationId());
            organizations = jdbc.query(sql, params, (rs, rowNum) -> new OrganizationView(
                    rs.getString("PUBLIC_ID"), rs.getString("ORGANIZATION_CODE"),
                    rs.getString("ORGANIZATION_NAME")));
        }
        return new AvailabilityView(question.mode(), organizations);
    }

    @Transactional
    public AvailabilityView update(String questionPublicId, UpdateCommand command, TenantContext tenant,
            Actor actor) {
        if (tenant == null || !tenant.globalAdministrator()) {
            throw new BusinessException("QUESTION_AVAILABILITY_FORBIDDEN",
                    "La disponibilidad organizacional solo puede modificarse por un Administrador global sobre una pregunta global.");
        }
        if (command == null || command.mode() == null) {
            throw new BusinessException("QUESTION_AVAILABILITY_REQUIRED",
                    "Selecciona una configuración de disponibilidad válida.");
        }
        if (actor == null || actor.userId() == null) {
            throw new BusinessException("QUESTION_ACTOR_REQUIRED",
                    "No fue posible identificar al usuario que realiza la operación.");
        }

        QuestionRow question = findGlobalQuestionForUpdate(questionPublicId);
        QuestionAvailabilityMode mode = command.mode();
        Set<String> requested = normalizeIds(command.organizationPublicIds());
        if (mode == QuestionAvailabilityMode.SELECTED_ORGANIZATIONS && requested.isEmpty()) {
            throw new BusinessException("QUESTION_ORGANIZATIONS_REQUIRED",
                    "Selecciona al menos una organización para esta pregunta.");
        }

        List<OrganizationRow> organizations = mode == QuestionAvailabilityMode.SELECTED_ORGANIZATIONS
                ? resolveOrganizations(requested) : List.of();
        if (organizations.size() != requested.size()) {
            throw new BusinessException("QUESTION_ORGANIZATION_INVALID",
                    "Una o más organizaciones no existen, no están activas o no son comerciales.");
        }

        Instant now = clock.instant();
        List<String> previous = activeOrganizationPublicIds(question.id());
        int updated = jdbc.update(
                "UPDATE QUESTION SET AVAILABILITY_MODE = :mode, UPDATED_BY = :actor, UPDATED_AT = :now "
                        + "WHERE QUESTION_ID = :questionId AND CONTENT_SCOPE = 'GLOBAL' AND STATUS <> 'DELETED'",
                new MapSqlParameterSource().addValue("mode", mode.name()).addValue("actor", actor.userId())
                        .addValue("now", now).addValue("questionId", question.id()));
        if (updated != 1) {
            throw new BusinessException("QUESTION_CONCURRENT_MODIFICATION",
                    "La pregunta cambió durante la operación. Recarga la información e inténtalo nuevamente.");
        }

        jdbc.update("""
            UPDATE QUESTION_ORGANIZATION_AVAILABILITY
               SET STATUS = 'INACTIVE', DISABLED_AT = :now, DISABLED_BY = :actor,
                   UPDATED_AT = :now, VERSION_NO = VERSION_NO + 1
             WHERE QUESTION_ID = :questionId AND STATUS = 'ACTIVE'
            """, new MapSqlParameterSource().addValue("now", now).addValue("actor", actor.userId())
                .addValue("questionId", question.id()));

        if (mode == QuestionAvailabilityMode.SELECTED_ORGANIZATIONS) {
            for (OrganizationRow organization : organizations) {
                jdbc.update("""
                    MERGE INTO QUESTION_ORGANIZATION_AVAILABILITY target
                    USING (SELECT :questionId QUESTION_ID, :organizationId ORGANIZATION_ID FROM DUAL) source
                       ON (target.QUESTION_ID = source.QUESTION_ID
                           AND target.ORGANIZATION_ID = source.ORGANIZATION_ID)
                    WHEN MATCHED THEN UPDATE SET
                        target.STATUS = 'ACTIVE', target.ENABLED_AT = :now, target.ENABLED_BY = :actor,
                        target.DISABLED_AT = NULL, target.DISABLED_BY = NULL, target.UPDATED_AT = :now,
                        target.VERSION_NO = target.VERSION_NO + 1
                    WHEN NOT MATCHED THEN INSERT
                        (QUESTION_ID, ORGANIZATION_ID, STATUS, ENABLED_AT, ENABLED_BY,
                         CREATED_AT, UPDATED_AT, VERSION_NO)
                    VALUES
                        (:questionId, :organizationId, 'ACTIVE', :now, :actor, :now, :now, 0)
                    """, new MapSqlParameterSource().addValue("questionId", question.id())
                        .addValue("organizationId", organization.id()).addValue("now", now)
                        .addValue("actor", actor.userId()));
            }
        }

        List<String> current = organizations.stream().map(OrganizationRow::publicId).toList();
        Map<String, Object> data = new HashMap<>();
        data.put("questionPublicId", questionPublicId);
        data.put("previousMode", question.mode().name());
        data.put("newMode", mode.name());
        data.put("previousOrganizations", previous);
        data.put("newOrganizations", current);
        audit.record(actor.userId(), "QUESTION_AVAILABILITY_CHANGED", "QUESTION_BANK",
                "Se actualizó la disponibilidad de la pregunta.", actor.ipAddress(), actor.userAgent(), data, now);

        return new AvailabilityView(mode, organizations.stream()
                .map(row -> new OrganizationView(row.publicId(), row.code(), row.name())).toList());
    }

    private QuestionRow findVisibleQuestion(String publicId, TenantContext tenant) {
        if (tenant == null) {
            throw new BusinessException("TENANT_REQUIRED", "No existe un contexto autorizado.");
        }
        if (!tenant.globalAdministrator() && !tenant.hasOrganization()) {
            throw new BusinessException("TENANT_REQUIRED", "No existe una organización autorizada en la sesión.");
        }

        String normalizedPublicId = PublicIdNormalizer.requiredUuid(publicId,
                "QUESTION_ID_INVALID", "La pregunta indicada no es válida.");
        String tenantClause = tenant.globalAdministrator() ? "" : """
            AND (
                (q.CONTENT_SCOPE = 'ORGANIZATION' AND q.OWNER_ORGANIZATION_ID = :tenantOrganizationId)
                OR (q.CONTENT_SCOPE = 'GLOBAL' AND (
                    NVL(q.AVAILABILITY_MODE, 'NONE') = 'GLOBAL'
                    OR (
                        NVL(q.AVAILABILITY_MODE, 'NONE') = 'SELECTED_ORGANIZATIONS'
                        AND EXISTS (
                            SELECT 1 FROM QUESTION_ORGANIZATION_AVAILABILITY a
                             WHERE a.QUESTION_ID = q.QUESTION_ID
                               AND a.ORGANIZATION_ID = :tenantOrganizationId
                               AND a.STATUS = 'ACTIVE'
                        )
                    )
                ))
            )
            """;
        MapSqlParameterSource params = new MapSqlParameterSource("publicId", normalizedPublicId);
        if (!tenant.globalAdministrator()) params.addValue("tenantOrganizationId", tenant.organizationId());

        List<QuestionRow> rows = jdbc.query("""
            SELECT q.QUESTION_ID, q.CONTENT_SCOPE, NVL(q.AVAILABILITY_MODE, 'NONE') AVAILABILITY_MODE
              FROM QUESTION q
             WHERE q.PUBLIC_ID = :publicId AND q.STATUS <> 'DELETED'
            """ + tenantClause, params, (rs, rowNum) -> new QuestionRow(rs.getLong("QUESTION_ID"),
                rs.getString("CONTENT_SCOPE"), QuestionAvailabilityMode.valueOf(rs.getString("AVAILABILITY_MODE"))));
        if (rows.isEmpty()) {
            throw new BusinessException("QUESTION_NOT_FOUND", "La pregunta no existe o no está disponible.");
        }
        return rows.getFirst();
    }

    private QuestionRow findGlobalQuestionForUpdate(String publicId) {
        String normalizedPublicId = PublicIdNormalizer.requiredUuid(publicId,
                "QUESTION_ID_INVALID", "La pregunta indicada no es válida.");
        List<QuestionRow> rows = jdbc.query("""
            SELECT QUESTION_ID, CONTENT_SCOPE, NVL(AVAILABILITY_MODE, 'NONE') AVAILABILITY_MODE
              FROM QUESTION
             WHERE PUBLIC_ID = :publicId
               AND STATUS <> 'DELETED'
               AND CONTENT_SCOPE = 'GLOBAL'
             FOR UPDATE
            """, Map.of("publicId", normalizedPublicId), (rs, rowNum) -> new QuestionRow(
                rs.getLong("QUESTION_ID"), rs.getString("CONTENT_SCOPE"),
                QuestionAvailabilityMode.valueOf(rs.getString("AVAILABILITY_MODE"))));
        if (rows.isEmpty()) {
            throw new BusinessException("QUESTION_GLOBAL_REQUIRED",
                    "La disponibilidad transversal solo puede configurarse en preguntas propiedad de GLOBAL.");
        }
        return rows.getFirst();
    }

    private List<OrganizationRow> resolveOrganizations(Set<String> publicIds) {
        if (publicIds.isEmpty()) return List.of();
        return jdbc.query("""
            SELECT ORGANIZATION_ID, PUBLIC_ID, ORGANIZATION_CODE, ORGANIZATION_NAME
              FROM ORGANIZATION
             WHERE PUBLIC_ID IN (:publicIds)
               AND ORGANIZATION_TYPE = 'CUSTOMER'
               AND STATUS = 'ACTIVE'
               AND VALID_FROM <= TRUNC(SYSDATE)
               AND (EXPIRES_ON IS NULL OR EXPIRES_ON >= TRUNC(SYSDATE))
            """, Map.of("publicIds", publicIds), (rs, rowNum) -> new OrganizationRow(
                rs.getLong("ORGANIZATION_ID"), rs.getString("PUBLIC_ID"),
                rs.getString("ORGANIZATION_CODE"), rs.getString("ORGANIZATION_NAME")));
    }

    private List<String> activeOrganizationPublicIds(Long questionId) {
        return jdbc.query("""
            SELECT o.PUBLIC_ID
              FROM QUESTION_ORGANIZATION_AVAILABILITY a
              JOIN ORGANIZATION o ON o.ORGANIZATION_ID = a.ORGANIZATION_ID
             WHERE a.QUESTION_ID = :questionId AND a.STATUS = 'ACTIVE'
             ORDER BY o.PUBLIC_ID
            """, Map.of("questionId", questionId), (rs, rowNum) -> rs.getString(1));
    }

    private Set<String> normalizeIds(List<String> values) {
        Set<String> result = new LinkedHashSet<>();
        if (values == null) return result;
        for (String value : values) {
            if (value != null && !value.isBlank()) result.add(value.trim());
        }
        return result;
    }

    public record Actor(Long userId, String ipAddress, String userAgent) {}
    public record UpdateCommand(QuestionAvailabilityMode mode, List<String> organizationPublicIds) {}
    public record AvailabilityView(QuestionAvailabilityMode mode, List<OrganizationView> organizations) {
        public AvailabilityView {
            organizations = organizations == null ? List.of() : List.copyOf(organizations);
        }
    }
    public record OrganizationView(String publicId, String code, String name) {}
    private record QuestionRow(Long id, String scope, QuestionAvailabilityMode mode) {}
    private record OrganizationRow(Long id, String publicId, String code, String name) {}
}
