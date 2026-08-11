package com.nexoskill.evaluation.development.application.service;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.authentication.infrastructure.security.AuthenticatedUser;
import com.nexoskill.evaluation.development.application.model.StudentDevelopmentModels.ActivityItem;
import com.nexoskill.evaluation.development.application.model.StudentDevelopmentModels.AssignPathCommand;
import com.nexoskill.evaluation.development.application.model.StudentDevelopmentModels.AssignedPathAdmin;
import com.nexoskill.evaluation.development.application.model.StudentDevelopmentModels.PathCard;
import com.nexoskill.evaluation.development.application.model.StudentDevelopmentModels.PathOption;
import com.nexoskill.evaluation.development.application.model.StudentDevelopmentModels.PathStage;
import com.nexoskill.evaluation.organizations.domain.model.TenantContext;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import com.nexoskill.evaluation.students.infrastructure.security.AuthenticatedStudent;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentPathService {
    private final NamedParameterJdbcTemplate jdbc;
    private final AuditLogPort audit;
    private final HttpServletRequest request;
    private final Clock clock;

    public StudentPathService(NamedParameterJdbcTemplate jdbc, AuditLogPort audit,
            HttpServletRequest request, Clock clock) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.request = request;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<PathOption> assignablePaths(TenantContext tenant, String studentPublicId) {
        StudentTarget target = resolveStudent(tenant, studentPublicId);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("studentId", target.studentId())
                .addValue("organizationId", target.organizationId());
        return jdbc.query("""
                SELECT path_value.PUBLIC_ID, path_value.PATH_NAME, path_value.DESCRIPTION,
                       path_value.CONTENT_SCOPE, organization_value.ORGANIZATION_NAME,
                       (SELECT COUNT(*) FROM LEARNING_PATH_COLLECTION relation_value
                         WHERE relation_value.PATH_ID=path_value.PATH_ID) COLLECTION_COUNT,
                       (SELECT COUNT(DISTINCT level_value.FORM_ID)
                          FROM LEARNING_PATH_COLLECTION relation_value
                          JOIN LEARNING_COLLECTION_LEVEL level_value ON level_value.COLLECTION_ID=relation_value.COLLECTION_ID
                         WHERE relation_value.PATH_ID=path_value.PATH_ID) FORM_COUNT
                  FROM LEARNING_PATH path_value
                  JOIN ORGANIZATION organization_value ON organization_value.ORGANIZATION_ID=path_value.OWNER_ORGANIZATION_ID
                 WHERE path_value.STATUS='ACTIVE'
                   AND (path_value.CONTENT_SCOPE='GLOBAL'
                        OR (path_value.CONTENT_SCOPE='ORGANIZATION' AND path_value.OWNER_ORGANIZATION_ID=:organizationId))
                   AND NOT EXISTS (
                        SELECT 1 FROM STUDENT_PATH_ASSIGNMENT assigned_value
                         WHERE assigned_value.STUDENT_ID=:studentId AND assigned_value.PATH_ID=path_value.PATH_ID
                           AND assigned_value.STATUS='ACTIVE'
                   )
                   AND NOT EXISTS (
                        SELECT 1
                          FROM LEARNING_PATH_COLLECTION relation_value
                          JOIN LEARNING_COLLECTION collection_value ON collection_value.COLLECTION_ID=relation_value.COLLECTION_ID
                         WHERE relation_value.PATH_ID=path_value.PATH_ID
                           AND NOT (
                                collection_value.STATUS='ACTIVE'
                                AND (
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
                                )
                           )
                   )
                 ORDER BY UPPER(path_value.PATH_NAME), path_value.PUBLIC_ID
                """, params, (rs, rowNum) -> new PathOption(rs.getString("PUBLIC_ID"), rs.getString("PATH_NAME"),
                        rs.getString("DESCRIPTION"), rs.getInt("COLLECTION_COUNT"), rs.getInt("FORM_COUNT"),
                        rs.getString("CONTENT_SCOPE"), rs.getString("ORGANIZATION_NAME")));
    }

    @Transactional(readOnly = true)
    public List<AssignedPathAdmin> assignedPaths(TenantContext tenant, String studentPublicId) {
        StudentTarget target = resolveStudent(tenant, studentPublicId);
        return jdbc.query("""
                SELECT assignment.PUBLIC_ID ASSIGNMENT_PUBLIC_ID, path_value.PUBLIC_ID PATH_PUBLIC_ID,
                       path_value.PATH_NAME, path_value.DESCRIPTION, assignment.STATUS, assignment.ASSIGNED_AT,
                       (SELECT COUNT(*) FROM LEARNING_PATH_COLLECTION relation_value
                         WHERE relation_value.PATH_ID=path_value.PATH_ID) COLLECTION_COUNT,
                       (SELECT COUNT(DISTINCT level_value.FORM_ID)
                          FROM LEARNING_PATH_COLLECTION relation_value
                          JOIN LEARNING_COLLECTION_LEVEL level_value ON level_value.COLLECTION_ID=relation_value.COLLECTION_ID
                         WHERE relation_value.PATH_ID=path_value.PATH_ID) FORM_COUNT
                  FROM STUDENT_PATH_ASSIGNMENT assignment
                  JOIN LEARNING_PATH path_value ON path_value.PATH_ID=assignment.PATH_ID
                 WHERE assignment.STUDENT_ID=:studentId AND assignment.ORGANIZATION_ID=:organizationId
                   AND assignment.STATUS='ACTIVE'
                 ORDER BY assignment.ASSIGNED_AT DESC, UPPER(path_value.PATH_NAME)
                """, Map.of("studentId", target.studentId(), "organizationId", target.organizationId()),
                (rs, rowNum) -> new AssignedPathAdmin(rs.getString("ASSIGNMENT_PUBLIC_ID"),
                        rs.getString("PATH_PUBLIC_ID"), rs.getString("PATH_NAME"), rs.getString("DESCRIPTION"),
                        rs.getString("STATUS"), rs.getInt("COLLECTION_COUNT"), rs.getInt("FORM_COUNT"),
                        rs.getObject("ASSIGNED_AT", OffsetDateTime.class)));
    }

    @Transactional
    public AssignedPathAdmin assign(TenantContext tenant, AuthenticatedUser actor, String studentPublicId,
            AssignPathCommand command) {
        requireActor(actor);
        StudentTarget target = resolveStudent(tenant, studentPublicId);
        String pathPublicId = canonical(command == null ? null : command.pathPublicId(), "PATH_ID_INVALID",
                "Selecciona un Path válido.");
        PathRow path = requireAssignablePath(target, pathPublicId);
        String assignmentPublicId = UUID.randomUUID().toString();
        try {
            jdbc.update("""
                    INSERT INTO STUDENT_PATH_ASSIGNMENT
                        (PUBLIC_ID, STUDENT_ID, ORGANIZATION_ID, PATH_ID, STATUS, ASSIGNED_BY, ASSIGNED_AT, UPDATED_AT, VERSION_NO)
                    VALUES (:publicId, :studentId, :organizationId, :pathId, 'ACTIVE', :actorId, SYSTIMESTAMP, SYSTIMESTAMP, 0)
                    """, new MapSqlParameterSource().addValue("publicId", assignmentPublicId)
                            .addValue("studentId", target.studentId()).addValue("organizationId", target.organizationId())
                            .addValue("pathId", path.id()).addValue("actorId", actor.internalId()));
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("PATH_ALREADY_ASSIGNED", "El Path ya está asignado a este colaborador.");
        }
        synchronizeStudent(target.studentId(), target.organizationId());
        record(actor, "PATH_ASSIGNED", "Se asignó el Path " + path.name() + ".", target.publicId(),
                Map.of("studentPublicId", target.publicId(), "pathPublicId", path.publicId(),
                        "pathAssignmentPublicId", assignmentPublicId, "pathName", path.name()));
        return assignedPaths(tenant, studentPublicId).stream()
                .filter(item -> item.assignmentPublicId().equals(assignmentPublicId)).findFirst()
                .orElseThrow(() -> new BusinessException("PATH_ASSIGNMENT_NOT_FOUND", "No fue posible recuperar el Path asignado."));
    }

    @Transactional
    public void remove(TenantContext tenant, AuthenticatedUser actor, String studentPublicId, String assignmentPublicId) {
        requireActor(actor);
        StudentTarget target = resolveStudent(tenant, studentPublicId);
        String canonicalAssignment = canonical(assignmentPublicId, "PATH_ASSIGNMENT_ID_INVALID", "La asignación indicada no es válida.");
        List<AssignmentPathRow> rows = jdbc.query("""
                SELECT assignment.PATH_ASSIGNMENT_ID, assignment.PUBLIC_ID, assignment.PATH_ID, path_value.PUBLIC_ID PATH_PUBLIC_ID,
                       path_value.PATH_NAME
                  FROM STUDENT_PATH_ASSIGNMENT assignment
                  JOIN LEARNING_PATH path_value ON path_value.PATH_ID=assignment.PATH_ID
                 WHERE assignment.PUBLIC_ID=:publicId AND assignment.STUDENT_ID=:studentId
                   AND assignment.ORGANIZATION_ID=:organizationId AND assignment.STATUS='ACTIVE'
                 FOR UPDATE
                """, new MapSqlParameterSource().addValue("publicId", canonicalAssignment)
                        .addValue("studentId", target.studentId()).addValue("organizationId", target.organizationId()),
                (rs, rowNum) -> new AssignmentPathRow(rs.getLong("PATH_ASSIGNMENT_ID"), rs.getString("PUBLIC_ID"),
                        rs.getLong("PATH_ID"), rs.getString("PATH_PUBLIC_ID"), rs.getString("PATH_NAME")));
        if (rows.isEmpty()) throw new BusinessException("PATH_ASSIGNMENT_NOT_FOUND", "El Path ya no está asignado a este colaborador.");
        AssignmentPathRow assignment = rows.getFirst();
        jdbc.update("""
                UPDATE STUDENT_PATH_ASSIGNMENT
                   SET STATUS='REMOVED', REMOVED_BY=:actorId, REMOVED_AT=SYSTIMESTAMP,
                       UPDATED_AT=SYSTIMESTAMP, VERSION_NO=VERSION_NO+1
                 WHERE PATH_ASSIGNMENT_ID=:id
                """, new MapSqlParameterSource().addValue("actorId", actor.internalId()).addValue("id", assignment.id()));
        jdbc.update("""
                UPDATE STUDENT_FORM_ASSIGNMENT_ORIGIN
                   SET STATUS='REMOVED', REMOVED_AT=SYSTIMESTAMP
                 WHERE PATH_ASSIGNMENT_ID=:pathAssignmentId AND STATUS='ACTIVE'
                """, Map.of("pathAssignmentId", assignment.id()));
        cancelOrphanPathAssignments(target.studentId(), target.organizationId());
        record(actor, "PATH_REMOVED", "Se retiró el Path " + assignment.pathName() + ".", target.publicId(),
                Map.of("studentPublicId", target.publicId(), "pathPublicId", assignment.pathPublicId(),
                        "pathAssignmentPublicId", assignment.publicId(), "pathName", assignment.pathName()));
    }

    @Transactional
    public void ensureAvailability(AuthenticatedStudent student) {
        if (student == null) throw new BusinessException("AUTHENTICATION_REQUIRED", "Debes iniciar sesión nuevamente.");
        synchronizeStudent(student.internalId(), student.organizationId());
    }

    @Transactional
    public List<PathCard> mine(AuthenticatedStudent student) {
        ensureAvailability(student);
        List<MinePathRow> paths = jdbc.query("""
                SELECT assignment.PATH_ASSIGNMENT_ID, assignment.PUBLIC_ID ASSIGNMENT_PUBLIC_ID,
                       path_value.PATH_ID, path_value.PUBLIC_ID PATH_PUBLIC_ID, path_value.PATH_NAME,
                       path_value.DESCRIPTION, assignment.ASSIGNED_AT
                  FROM STUDENT_PATH_ASSIGNMENT assignment
                  JOIN LEARNING_PATH path_value ON path_value.PATH_ID=assignment.PATH_ID
                 WHERE assignment.STUDENT_ID=:studentId AND assignment.ORGANIZATION_ID=:organizationId
                   AND assignment.STATUS='ACTIVE' AND path_value.STATUS='ACTIVE'
                 ORDER BY assignment.ASSIGNED_AT, path_value.PATH_NAME
                """, Map.of("studentId", student.internalId(), "organizationId", student.organizationId()),
                (rs, rowNum) -> new MinePathRow(rs.getLong("PATH_ASSIGNMENT_ID"), rs.getString("ASSIGNMENT_PUBLIC_ID"),
                        rs.getLong("PATH_ID"), rs.getString("PATH_PUBLIC_ID"), rs.getString("PATH_NAME"),
                        rs.getString("DESCRIPTION"), rs.getObject("ASSIGNED_AT", OffsetDateTime.class)));
        if (paths.isEmpty()) return List.of();

        List<Long> pathAssignmentIds = paths.stream().map(MinePathRow::assignmentId).toList();
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("assignmentIds", pathAssignmentIds)
                .addValue("studentId", student.internalId()).addValue("organizationId", student.organizationId());
        List<StageRow> stageRows = jdbc.query("""
                WITH form_progress AS (
                    SELECT assignment.FORM_ID,
                           MAX(CASE WHEN attempt.EVALUATION_ATTEMPT_ID IS NOT NULL THEN 1 ELSE 0 END) HAS_ATTEMPT,
                           MAX(CASE WHEN attempt.PASSED=1 THEN 1 ELSE 0 END) HAS_PASSED
                      FROM STUDENT_FORM_ASSIGNMENT assignment
                      LEFT JOIN STUDENT_EVALUATION_ATTEMPT attempt
                        ON attempt.FORM_ASSIGNMENT_ID=assignment.FORM_ASSIGNMENT_ID
                       AND attempt.STATUS IN ('COMPLETED','PENDING_REVIEW','EXPIRED')
                     WHERE assignment.STUDENT_ID=:studentId AND assignment.ORGANIZATION_ID=:organizationId
                     GROUP BY assignment.FORM_ID
                ), active_assignment AS (
                    SELECT FORM_ID, PUBLIC_ID, ASSIGNED_AT,
                           ROW_NUMBER() OVER (PARTITION BY FORM_ID ORDER BY
                                CASE STATUS WHEN 'IN_PROGRESS' THEN 1 WHEN 'ASSIGNED' THEN 2 ELSE 3 END,
                                ASSIGNED_AT DESC, FORM_ASSIGNMENT_ID DESC) RN
                      FROM STUDENT_FORM_ASSIGNMENT
                     WHERE STUDENT_ID=:studentId AND ORGANIZATION_ID=:organizationId
                       AND STATUS IN ('ASSIGNED','IN_PROGRESS')
                )
                SELECT path_assignment.PATH_ASSIGNMENT_ID, relation_value.COLLECTION_ORDER,
                       collection_value.PUBLIC_ID COLLECTION_PUBLIC_ID, collection_value.COLLECTION_NAME,
                       collection_value.STATUS COLLECTION_STATUS,
                       COUNT(DISTINCT CASE WHEN form_value.STATUS='ACTIVE' AND form_value.MODE_CODE='ASSESSMENT'
                                           THEN form_value.FORM_ID END) TOTAL_FORMS,
                       COUNT(DISTINCT CASE WHEN form_value.STATUS='ACTIVE' AND form_value.MODE_CODE='ASSESSMENT'
                                                AND NVL(progress.HAS_PASSED,0)=1 THEN form_value.FORM_ID END) PASSED_FORMS,
                       COUNT(DISTINCT CASE WHEN form_value.STATUS='ACTIVE' AND form_value.MODE_CODE='ASSESSMENT'
                                                AND NVL(progress.HAS_ATTEMPT,0)=1 THEN form_value.FORM_ID END) ATTEMPTED_FORMS,
                       MAX(CASE WHEN form_value.STATUS='ACTIVE' AND form_value.MODE_CODE='ASSESSMENT'
                                     AND NVL(progress.HAS_PASSED,0)=0 AND active_value.RN=1
                                THEN active_value.PUBLIC_ID END)
                           KEEP (DENSE_RANK FIRST ORDER BY
                                CASE WHEN form_value.STATUS='ACTIVE' AND form_value.MODE_CODE='ASSESSMENT'
                                          AND NVL(progress.HAS_PASSED,0)=0 AND active_value.RN=1
                                     THEN level_value.LEVEL_ORDER ELSE 999999 END) NEXT_ASSIGNMENT_PUBLIC_ID
                  FROM STUDENT_PATH_ASSIGNMENT path_assignment
                  JOIN LEARNING_PATH_COLLECTION relation_value ON relation_value.PATH_ID=path_assignment.PATH_ID
                  JOIN LEARNING_COLLECTION collection_value ON collection_value.COLLECTION_ID=relation_value.COLLECTION_ID
                  LEFT JOIN LEARNING_COLLECTION_LEVEL level_value ON level_value.COLLECTION_ID=collection_value.COLLECTION_ID
                  LEFT JOIN EVALUATION_FORM form_value ON form_value.FORM_ID=level_value.FORM_ID
                  LEFT JOIN form_progress progress ON progress.FORM_ID=form_value.FORM_ID
                  LEFT JOIN active_assignment active_value ON active_value.FORM_ID=form_value.FORM_ID
                 WHERE path_assignment.PATH_ASSIGNMENT_ID IN (:assignmentIds)
                 GROUP BY path_assignment.PATH_ASSIGNMENT_ID, relation_value.COLLECTION_ORDER,
                          collection_value.PUBLIC_ID, collection_value.COLLECTION_NAME, collection_value.STATUS
                 ORDER BY path_assignment.PATH_ASSIGNMENT_ID, relation_value.COLLECTION_ORDER
                """, params, (rs, rowNum) -> new StageRow(rs.getLong("PATH_ASSIGNMENT_ID"),
                        rs.getInt("COLLECTION_ORDER"), rs.getString("COLLECTION_PUBLIC_ID"),
                        rs.getString("COLLECTION_NAME"), rs.getString("COLLECTION_STATUS"), rs.getInt("TOTAL_FORMS"),
                        rs.getInt("PASSED_FORMS"), rs.getInt("ATTEMPTED_FORMS"), rs.getString("NEXT_ASSIGNMENT_PUBLIC_ID")));
        Map<Long, List<StageRow>> stagesByAssignment = stageRows.stream()
                .collect(Collectors.groupingBy(StageRow::pathAssignmentId, LinkedHashMap::new, Collectors.toList()));

        List<PathCard> result = new ArrayList<>();
        for (MinePathRow path : paths) {
            List<StageRow> rows = stagesByAssignment.getOrDefault(path.assignmentId(), List.of());
            List<PathStage> stages = rows.stream().map(this::toStage).toList();
            long completed = stages.stream().filter(stage -> "COMPLETED".equals(stage.state())).count();
            boolean complete = !stages.isEmpty() && completed == stages.size();
            PathStage next = stages.stream().filter(stage -> !"COMPLETED".equals(stage.state())
                    && !"UNAVAILABLE".equals(stage.state()) && !"EMPTY".equals(stage.state())).findFirst().orElse(null);
            if (next == null) next = stages.stream().filter(stage -> !"COMPLETED".equals(stage.state())).findFirst().orElse(null);
            String status = stages.isEmpty() ? "EMPTY" : complete ? "COMPLETED"
                    : stages.stream().anyMatch(stage -> "IN_PROGRESS".equals(stage.state())) || completed > 0 ? "IN_PROGRESS" : "ASSIGNED";
            result.add(new PathCard(path.assignmentPublicId(), path.pathPublicId(), path.name(), path.description(),
                    status, (int) completed, stages.size(), null, next == null ? null : next.title(),
                    next == null ? null : next.nextEvaluationAssignmentPublicId(), stages));
        }
        return List.copyOf(result);
    }

    @Transactional
    public PathCard mine(AuthenticatedStudent student, String assignmentPublicId) {
        String target = canonical(assignmentPublicId, "PATH_ASSIGNMENT_ID_INVALID", "El Path indicado no es válido.");
        return mine(student).stream().filter(item -> item.assignmentPublicId().equals(target)).findFirst()
                .orElseThrow(() -> new BusinessException("PATH_ASSIGNMENT_NOT_FOUND", "El Path no existe o no pertenece a tu cuenta."));
    }

    @Transactional(readOnly = true)
    public List<ActivityItem> recentActivity(AuthenticatedStudent student, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 20));
        return jdbc.query("""
                SELECT OCCURRED_AT, EVENT_TYPE, DESCRIPTION
                  FROM (
                        SELECT event_value.OCCURRED_AT, event_value.EVENT_TYPE, event_value.DESCRIPTION,
                               ROW_NUMBER() OVER (ORDER BY event_value.OCCURRED_AT DESC, event_value.AUDIT_EVENT_ID DESC) RN
                          FROM AUDIT_EVENT event_value
                         WHERE event_value.MODULE_CODE='PATHS'
                           AND event_value.EVENT_TYPE IN ('PATH_ASSIGNED','PATH_REMOVED')
                           AND event_value.EVENT_DATA IS NOT NULL
                           AND DBMS_LOB.INSTR(event_value.EVENT_DATA, :studentPublicId) > 0
                       )
                 WHERE RN<=:limit
                 ORDER BY OCCURRED_AT DESC
                """, new MapSqlParameterSource().addValue("studentPublicId", student.publicId()).addValue("limit", safeLimit),
                (rs, rowNum) -> new ActivityItem(rs.getString("EVENT_TYPE"), timestamp(rs.getTimestamp("OCCURRED_AT")),
                        "PATH_ASSIGNED".equals(rs.getString("EVENT_TYPE")) ? "Se asignó un Path" : "Se retiró un Path",
                        rs.getString("DESCRIPTION")));
    }

    private PathStage toStage(StageRow row) {
        String state;
        if (!"ACTIVE".equals(row.collectionStatus())) state = "UNAVAILABLE";
        else if (row.totalForms() == 0) state = "EMPTY";
        else if (row.passedForms() == row.totalForms()) state = "COMPLETED";
        else if (row.attemptedForms() > 0) state = "IN_PROGRESS";
        else state = "PENDING";
        return new PathStage(row.collectionPublicId(), row.collectionName(), row.order(), state,
                row.passedForms(), row.totalForms(), row.nextAssignmentPublicId());
    }

    private void synchronizeStudent(Long studentId, Long organizationId) {
        MapSqlParameterSource owner = new MapSqlParameterSource().addValue("studentId", studentId)
                .addValue("organizationId", organizationId);
        jdbc.update("""
                UPDATE STUDENT_FORM_ASSIGNMENT_ORIGIN origin_value
                   SET STATUS='REMOVED', REMOVED_AT=SYSTIMESTAMP
                 WHERE origin_value.STATUS='ACTIVE'
                   AND EXISTS (
                        SELECT 1 FROM STUDENT_PATH_ASSIGNMENT path_assignment
                         WHERE path_assignment.PATH_ASSIGNMENT_ID=origin_value.PATH_ASSIGNMENT_ID
                           AND path_assignment.STUDENT_ID=:studentId AND path_assignment.ORGANIZATION_ID=:organizationId
                   )
                   AND NOT EXISTS (
                        SELECT 1
                          FROM STUDENT_PATH_ASSIGNMENT path_assignment
                          JOIN LEARNING_PATH path_value ON path_value.PATH_ID=path_assignment.PATH_ID AND path_value.STATUS='ACTIVE'
                          JOIN LEARNING_PATH_COLLECTION relation_value ON relation_value.PATH_ID=path_value.PATH_ID
                          JOIN LEARNING_COLLECTION collection_value ON collection_value.COLLECTION_ID=relation_value.COLLECTION_ID
                                                               AND collection_value.STATUS='ACTIVE'
                          JOIN LEARNING_COLLECTION_LEVEL level_value ON level_value.COLLECTION_ID=collection_value.COLLECTION_ID
                          JOIN EVALUATION_FORM form_value ON form_value.FORM_ID=level_value.FORM_ID
                                                       AND form_value.STATUS='ACTIVE' AND form_value.MODE_CODE='ASSESSMENT'
                          JOIN STUDENT_FORM_ASSIGNMENT form_assignment ON form_assignment.FORM_ASSIGNMENT_ID=origin_value.FORM_ASSIGNMENT_ID
                         WHERE path_assignment.PATH_ASSIGNMENT_ID=origin_value.PATH_ASSIGNMENT_ID
                           AND path_assignment.STATUS='ACTIVE'
                           AND form_assignment.FORM_ID=form_value.FORM_ID
                           AND (
                                level_value.UNLOCK_RULE='FIRST_AVAILABLE'
                                OR NOT EXISTS (
                                    SELECT 1
                                      FROM LEARNING_COLLECTION_LEVEL previous_level
                                      JOIN EVALUATION_FORM previous_form ON previous_form.FORM_ID=previous_level.FORM_ID
                                                                      AND previous_form.STATUS='ACTIVE'
                                                                      AND previous_form.MODE_CODE='ASSESSMENT'
                                     WHERE previous_level.COLLECTION_ID=collection_value.COLLECTION_ID
                                       AND previous_level.LEVEL_ORDER<level_value.LEVEL_ORDER
                                       AND NOT EXISTS (
                                            SELECT 1
                                              FROM STUDENT_FORM_ASSIGNMENT previous_assignment
                                              JOIN STUDENT_EVALUATION_ATTEMPT previous_attempt
                                                ON previous_attempt.FORM_ASSIGNMENT_ID=previous_assignment.FORM_ASSIGNMENT_ID
                                             WHERE previous_assignment.STUDENT_ID=:studentId
                                               AND previous_assignment.ORGANIZATION_ID=:organizationId
                                               AND previous_assignment.FORM_ID=previous_level.FORM_ID
                                               AND previous_attempt.PASSED=1
                                               AND previous_attempt.STATUS IN ('COMPLETED','PENDING_REVIEW')
                                       )
                                )
                           )
                   )
                """, owner);

        List<DesiredForm> desired = jdbc.query("""
                SELECT DISTINCT path_assignment.PATH_ASSIGNMENT_ID, path_assignment.ASSIGNED_BY, form_value.FORM_ID
                  FROM STUDENT_PATH_ASSIGNMENT path_assignment
                  JOIN LEARNING_PATH path_value ON path_value.PATH_ID=path_assignment.PATH_ID AND path_value.STATUS='ACTIVE'
                  JOIN LEARNING_PATH_COLLECTION relation_value ON relation_value.PATH_ID=path_value.PATH_ID
                  JOIN LEARNING_COLLECTION collection_value ON collection_value.COLLECTION_ID=relation_value.COLLECTION_ID
                                                           AND collection_value.STATUS='ACTIVE'
                  JOIN LEARNING_COLLECTION_LEVEL level_value ON level_value.COLLECTION_ID=collection_value.COLLECTION_ID
                  JOIN EVALUATION_FORM form_value ON form_value.FORM_ID=level_value.FORM_ID
                                                AND form_value.STATUS='ACTIVE' AND form_value.MODE_CODE='ASSESSMENT'
                 WHERE path_assignment.STUDENT_ID=:studentId AND path_assignment.ORGANIZATION_ID=:organizationId
                   AND path_assignment.STATUS='ACTIVE'
                   AND (
                        level_value.UNLOCK_RULE='FIRST_AVAILABLE'
                        OR NOT EXISTS (
                            SELECT 1
                              FROM LEARNING_COLLECTION_LEVEL previous_level
                              JOIN EVALUATION_FORM previous_form ON previous_form.FORM_ID=previous_level.FORM_ID
                                                              AND previous_form.STATUS='ACTIVE'
                                                              AND previous_form.MODE_CODE='ASSESSMENT'
                             WHERE previous_level.COLLECTION_ID=collection_value.COLLECTION_ID
                               AND previous_level.LEVEL_ORDER<level_value.LEVEL_ORDER
                               AND NOT EXISTS (
                                    SELECT 1
                                      FROM STUDENT_FORM_ASSIGNMENT previous_assignment
                                      JOIN STUDENT_EVALUATION_ATTEMPT previous_attempt
                                        ON previous_attempt.FORM_ASSIGNMENT_ID=previous_assignment.FORM_ASSIGNMENT_ID
                                     WHERE previous_assignment.STUDENT_ID=:studentId
                                       AND previous_assignment.ORGANIZATION_ID=:organizationId
                                       AND previous_assignment.FORM_ID=previous_level.FORM_ID
                                       AND previous_attempt.PASSED=1
                                       AND previous_attempt.STATUS IN ('COMPLETED','PENDING_REVIEW')
                               )
                        )
                   )
                """, owner, (rs, rowNum) -> new DesiredForm(rs.getLong("PATH_ASSIGNMENT_ID"),
                        rs.getLong("ASSIGNED_BY"), rs.getLong("FORM_ID")));
        Map<Long, Long> assignmentByForm = new HashMap<>();
        for (DesiredForm item : desired) {
            Long formAssignmentId = assignmentByForm.computeIfAbsent(item.formId(),
                    ignored -> ensureFormAssignment(studentId, organizationId, item.formId(), item.actorId()));
            ensureOrigin(item.pathAssignmentId(), formAssignmentId);
        }
        cancelOrphanPathAssignments(studentId, organizationId);
    }

    private Long ensureFormAssignment(Long studentId, Long organizationId, Long formId, Long actorId) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("studentId", studentId)
                .addValue("organizationId", organizationId).addValue("formId", formId);
        List<Long> active = jdbc.query("""
                SELECT FORM_ASSIGNMENT_ID FROM STUDENT_FORM_ASSIGNMENT
                 WHERE STUDENT_ID=:studentId AND ORGANIZATION_ID=:organizationId AND FORM_ID=:formId
                   AND STATUS IN ('ASSIGNED','IN_PROGRESS')
                 ORDER BY CASE STATUS WHEN 'IN_PROGRESS' THEN 1 ELSE 2 END, ASSIGNED_AT DESC, FORM_ASSIGNMENT_ID DESC
                """, params, (rs, rowNum) -> rs.getLong("FORM_ASSIGNMENT_ID"));
        if (!active.isEmpty()) return active.getFirst();

        List<Long> completed = jdbc.query("""
                SELECT FORM_ASSIGNMENT_ID FROM STUDENT_FORM_ASSIGNMENT assignment
                 WHERE STUDENT_ID=:studentId AND ORGANIZATION_ID=:organizationId AND FORM_ID=:formId
                   AND STATUS='COMPLETED'
                 ORDER BY CASE WHEN EXISTS (
                            SELECT 1 FROM STUDENT_EVALUATION_ATTEMPT attempt
                             WHERE attempt.FORM_ASSIGNMENT_ID=assignment.FORM_ASSIGNMENT_ID AND attempt.PASSED=1
                        ) THEN 1 ELSE 2 END,
                        COMPLETED_AT DESC NULLS LAST, FORM_ASSIGNMENT_ID DESC
                """, params, (rs, rowNum) -> rs.getLong("FORM_ASSIGNMENT_ID"));
        if (!completed.isEmpty()) return completed.getFirst();

        List<Long> reusable = jdbc.query("""
                SELECT assignment.FORM_ASSIGNMENT_ID
                  FROM STUDENT_FORM_ASSIGNMENT assignment
                 WHERE assignment.STUDENT_ID=:studentId AND assignment.ORGANIZATION_ID=:organizationId
                   AND assignment.FORM_ID=:formId AND assignment.STATUS='CANCELLED' AND assignment.DIRECT_SOURCE=0
                   AND NOT EXISTS (SELECT 1 FROM STUDENT_EVALUATION_ATTEMPT attempt
                                    WHERE attempt.FORM_ASSIGNMENT_ID=assignment.FORM_ASSIGNMENT_ID)
                 ORDER BY assignment.UPDATED_AT DESC, assignment.FORM_ASSIGNMENT_ID DESC
                """, params, (rs, rowNum) -> rs.getLong("FORM_ASSIGNMENT_ID"));
        if (!reusable.isEmpty()) {
            Long id = reusable.getFirst();
            jdbc.update("""
                    UPDATE STUDENT_FORM_ASSIGNMENT
                       SET STATUS='ASSIGNED', COMPLETED_AT=NULL, UPDATED_AT=SYSTIMESTAMP, VERSION_NO=VERSION_NO+1
                     WHERE FORM_ASSIGNMENT_ID=:id
                    """, Map.of("id", id));
            return id;
        }

        String publicId = UUID.randomUUID().toString();
        MapSqlParameterSource insert = new MapSqlParameterSource().addValue("publicId", publicId)
                .addValue("studentId", studentId).addValue("organizationId", organizationId).addValue("formId", formId)
                .addValue("actorId", actorId);
        try {
            jdbc.update("""
                    INSERT INTO STUDENT_FORM_ASSIGNMENT
                        (PUBLIC_ID, STUDENT_ID, ORGANIZATION_ID, FORM_ID, STATUS, DIRECT_SOURCE,
                         ASSIGNED_BY, ASSIGNED_AT, UPDATED_AT, VERSION_NO)
                    VALUES (:publicId, :studentId, :organizationId, :formId, 'ASSIGNED', 0,
                            :actorId, SYSTIMESTAMP, SYSTIMESTAMP, 0)
                    """, insert);
        } catch (DuplicateKeyException exception) {
            List<Long> concurrent = jdbc.query("""
                    SELECT FORM_ASSIGNMENT_ID FROM STUDENT_FORM_ASSIGNMENT
                     WHERE STUDENT_ID=:studentId AND ORGANIZATION_ID=:organizationId AND FORM_ID=:formId
                       AND STATUS IN ('ASSIGNED','IN_PROGRESS')
                     ORDER BY FORM_ASSIGNMENT_ID DESC
                    """, params, (rs, rowNum) -> rs.getLong("FORM_ASSIGNMENT_ID"));
            if (!concurrent.isEmpty()) return concurrent.getFirst();
            throw exception;
        }
        return jdbc.queryForObject("SELECT FORM_ASSIGNMENT_ID FROM STUDENT_FORM_ASSIGNMENT WHERE PUBLIC_ID=:publicId",
                Map.of("publicId", publicId), Long.class);
    }

    private void ensureOrigin(Long pathAssignmentId, Long formAssignmentId) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("pathAssignmentId", pathAssignmentId)
                .addValue("formAssignmentId", formAssignmentId);
        Integer active = jdbc.queryForObject("""
                SELECT COUNT(*) FROM STUDENT_FORM_ASSIGNMENT_ORIGIN
                 WHERE PATH_ASSIGNMENT_ID=:pathAssignmentId AND FORM_ASSIGNMENT_ID=:formAssignmentId AND STATUS='ACTIVE'
                """, params, Integer.class);
        if (active != null && active > 0) return;
        List<Long> removed = jdbc.query("""
                SELECT FORM_ASSIGNMENT_ORIGIN_ID FROM STUDENT_FORM_ASSIGNMENT_ORIGIN
                 WHERE PATH_ASSIGNMENT_ID=:pathAssignmentId AND FORM_ASSIGNMENT_ID=:formAssignmentId AND STATUS='REMOVED'
                 ORDER BY FORM_ASSIGNMENT_ORIGIN_ID DESC
                """, params, (rs, rowNum) -> rs.getLong("FORM_ASSIGNMENT_ORIGIN_ID"));
        if (!removed.isEmpty()) {
            jdbc.update("""
                    UPDATE STUDENT_FORM_ASSIGNMENT_ORIGIN
                       SET STATUS='ACTIVE', REMOVED_AT=NULL
                     WHERE FORM_ASSIGNMENT_ORIGIN_ID=:id
                    """, Map.of("id", removed.getFirst()));
            return;
        }
        jdbc.update("""
                INSERT INTO STUDENT_FORM_ASSIGNMENT_ORIGIN
                    (PATH_ASSIGNMENT_ID, FORM_ASSIGNMENT_ID, STATUS, CREATED_AT)
                VALUES (:pathAssignmentId, :formAssignmentId, 'ACTIVE', SYSTIMESTAMP)
                """, params);
    }

    private void cancelOrphanPathAssignments(Long studentId, Long organizationId) {
        jdbc.update("""
                UPDATE STUDENT_FORM_ASSIGNMENT assignment
                   SET STATUS='CANCELLED', UPDATED_AT=SYSTIMESTAMP, VERSION_NO=VERSION_NO+1
                 WHERE assignment.STUDENT_ID=:studentId AND assignment.ORGANIZATION_ID=:organizationId
                   AND assignment.DIRECT_SOURCE=0 AND assignment.STATUS='ASSIGNED'
                   AND NOT EXISTS (
                        SELECT 1 FROM STUDENT_FORM_ASSIGNMENT_ORIGIN origin_value
                         WHERE origin_value.FORM_ASSIGNMENT_ID=assignment.FORM_ASSIGNMENT_ID
                           AND origin_value.STATUS='ACTIVE'
                   )
                """, Map.of("studentId", studentId, "organizationId", organizationId));
    }

    private PathRow requireAssignablePath(StudentTarget target, String publicId) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("publicId", publicId)
                .addValue("organizationId", target.organizationId());
        List<PathRow> rows = jdbc.query("""
                SELECT path_value.PATH_ID, path_value.PUBLIC_ID, path_value.PATH_NAME
                  FROM LEARNING_PATH path_value
                 WHERE path_value.PUBLIC_ID=:publicId AND path_value.STATUS='ACTIVE'
                   AND (path_value.CONTENT_SCOPE='GLOBAL'
                        OR (path_value.CONTENT_SCOPE='ORGANIZATION' AND path_value.OWNER_ORGANIZATION_ID=:organizationId))
                   AND NOT EXISTS (
                        SELECT 1
                          FROM LEARNING_PATH_COLLECTION relation_value
                          JOIN LEARNING_COLLECTION collection_value ON collection_value.COLLECTION_ID=relation_value.COLLECTION_ID
                         WHERE relation_value.PATH_ID=path_value.PATH_ID
                           AND NOT (
                                collection_value.STATUS='ACTIVE'
                                AND (
                                    (collection_value.CONTENT_SCOPE='ORGANIZATION' AND collection_value.OWNER_ORGANIZATION_ID=:organizationId)
                                    OR (collection_value.CONTENT_SCOPE='GLOBAL' AND (
                                        EXISTS (SELECT 1 FROM ORGANIZATION org_value
                                                 WHERE org_value.ORGANIZATION_ID=:organizationId AND org_value.CONTENT_MODE='GLOBAL_CATALOG')
                                        OR EXISTS (
                                            SELECT 1 FROM ORGANIZATION_GLOBAL_CONTENT_GRANT grant_value
                                             WHERE grant_value.ORGANIZATION_ID=:organizationId
                                               AND grant_value.CONTENT_TYPE='COLLECTION'
                                               AND grant_value.GLOBAL_CONTENT_ID=collection_value.COLLECTION_ID
                                               AND grant_value.STATUS='ACTIVE' AND grant_value.DISTRIBUTION_MODE='GLOBAL_REFERENCE'
                                               AND (grant_value.AVAILABLE_FROM IS NULL OR grant_value.AVAILABLE_FROM<=SYSTIMESTAMP)
                                               AND (grant_value.EXPIRES_AT IS NULL OR grant_value.EXPIRES_AT>SYSTIMESTAMP)
                                        )
                                    ))
                                )
                           )
                   )
                """, params, (rs, rowNum) -> new PathRow(rs.getLong("PATH_ID"), rs.getString("PUBLIC_ID"), rs.getString("PATH_NAME")));
        if (rows.isEmpty()) throw new BusinessException("PATH_NOT_ASSIGNABLE", "El Path no está disponible dentro de la organización del colaborador.");
        return rows.getFirst();
    }

    private StudentTarget resolveStudent(TenantContext tenant, String studentPublicId) {
        if (tenant == null) throw new BusinessException("TENANT_NOT_RESOLVED", "No fue posible determinar el alcance de la operación.");
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("publicId",
                canonical(studentPublicId, "STUDENT_ID_INVALID", "El colaborador indicado no es válido."));
        String scope;
        if (tenant.globalAdministrator() && tenant.globalScope()) scope = "";
        else if (tenant.hasOrganization()) {
            scope = " AND student.ORGANIZATION_ID=:organizationId";
            params.addValue("organizationId", tenant.organizationId());
        } else throw new BusinessException("TENANT_NOT_RESOLVED", "No fue posible determinar la organización.");
        List<StudentTarget> rows = jdbc.query("""
                SELECT student.STUDENT_ID, student.PUBLIC_ID, student.ORGANIZATION_ID, student.DISPLAY_NAME
                  FROM STUDENT student
                 WHERE student.PUBLIC_ID=:publicId AND student.STATUS<>'DELETED' AND student.RECORD_MODULE='COLLABORATOR'
                """ + scope, params, (rs, rowNum) -> new StudentTarget(rs.getLong("STUDENT_ID"), rs.getString("PUBLIC_ID"),
                        rs.getLong("ORGANIZATION_ID"), rs.getString("DISPLAY_NAME")));
        if (rows.isEmpty()) throw new BusinessException("STUDENT_NOT_FOUND", "El colaborador no existe dentro de tu alcance.");
        return rows.getFirst();
    }

    private String canonical(String value, String code, String message) {
        if (value == null || value.isBlank()) throw new BusinessException(code, message);
        try { return UUID.fromString(value.trim()).toString(); }
        catch (IllegalArgumentException exception) { throw new BusinessException(code, message); }
    }

    private void record(AuthenticatedUser actor, String type, String description, String studentPublicId,
            Map<String, Object> data) {
        audit.record(actor.internalId(), type, "PATHS", description, request.getRemoteAddr(), request.getHeader("User-Agent"),
                data, Instant.now(clock));
    }

    private static void requireActor(AuthenticatedUser actor) {
        if (actor == null) throw new BusinessException("AUTHENTICATION_REQUIRED", "Debes iniciar sesión nuevamente.");
    }
    private static Instant timestamp(Timestamp value) { return value == null ? Instant.EPOCH : value.toInstant(); }

    private record StudentTarget(Long studentId, String publicId, Long organizationId, String displayName) {}
    private record PathRow(Long id, String publicId, String name) {}
    private record AssignmentPathRow(Long id, String publicId, Long pathId, String pathPublicId, String pathName) {}
    private record DesiredForm(Long pathAssignmentId, Long actorId, Long formId) {}
    private record MinePathRow(Long assignmentId, String assignmentPublicId, Long pathId, String pathPublicId,
            String name, String description, OffsetDateTime assignedAt) {}
    private record StageRow(Long pathAssignmentId, int order, String collectionPublicId, String collectionName,
            String collectionStatus, int totalForms, int passedForms, int attemptedForms, String nextAssignmentPublicId) {}
}
