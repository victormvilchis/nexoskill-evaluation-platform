package com.nexoskill.evaluation.students.application;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.organizations.domain.model.TenantContext;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import com.nexoskill.evaluation.students.domain.StudentSessionRevocationReason;
import com.nexoskill.evaluation.students.domain.StudentSessionStatus;
import com.nexoskill.evaluation.students.infrastructure.persistence.StudentJpaEntity;
import com.nexoskill.evaluation.students.infrastructure.persistence.StudentRepository;
import com.nexoskill.evaluation.students.infrastructure.persistence.StudentSessionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Purga explícita, física y transaccional de una persona estudiante. */
@Service
public class StudentDeletionService {
    private final StudentRepository students;
    private final StudentSessionRepository sessions;
    private final NamedParameterJdbcTemplate jdbc;
    private final AuditLogPort audit;
    private final Clock clock;

    public StudentDeletionService(StudentRepository students, StudentSessionRepository sessions,
            NamedParameterJdbcTemplate jdbc, AuditLogPort audit, Clock clock) {
        this.students = students;
        this.sessions = sessions;
        this.jdbc = jdbc;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public DeletionResult deletePermanently(TenantContext tenant, String publicId, boolean confirmed,
            StudentService.Actor actor) {
        if (!confirmed) {
            throw new BusinessException("STUDENT_DELETE_CONFIRMATION_REQUIRED",
                    "Debes confirmar que comprendes que la eliminación es permanente.",
                    Map.of("confirmed", "Marca la confirmación para eliminar permanentemente al estudiante."));
        }
        if (tenant == null || !tenant.hasOrganization()) {
            throw new BusinessException("STUDENT_DELETE_FORBIDDEN",
                    "No existe un contexto organizacional autorizado para eliminar al estudiante.");
        }
        StudentJpaEntity student = students.findByOrganizationIdAndPublicIdForUpdate(tenant.organizationId(), publicId)
                .filter(candidate -> candidate.getStatus() != com.nexoskill.evaluation.students.domain.StudentStatus.DELETED)
                .orElseThrow(() -> new BusinessException("STUDENT_NOT_FOUND", "El estudiante no existe."));
        if (!student.getOrganizationId().equals(tenant.organizationId())) {
            throw new BusinessException("STUDENT_DELETE_FORBIDDEN",
                    "No tienes permisos para eliminar estudiantes de otra organización.");
        }

        Instant now = clock.instant();
        student.markDeleting(actor.userId(), now);
        students.saveAndFlush(student);
        sessions.revokeActive(student.getId(), StudentSessionStatus.ACTIVE, StudentSessionStatus.REVOKED,
                StudentSessionRevocationReason.DELETED, now);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("studentId", student.getId())
                .addValue("organizationId", student.getOrganizationId());

        // El historial referencia tanto al estudiante como al requisito: se elimina primero.
        jdbc.update("DELETE FROM STUDENT_CERTIFICATION_HISTORY WHERE STUDENT_ID = :studentId", params);
        jdbc.update("""
            DELETE FROM STUDENT_CERTIFICATION_ATTEMPT
             WHERE STUDENT_CERT_REQUIREMENT_ID IN (
                   SELECT requirement.STUDENT_CERT_REQUIREMENT_ID
                     FROM STUDENT_CERT_REQUIREMENT requirement
                     JOIN STUDENT_CERTIFICATION_PROFILE profile
                       ON profile.STUDENT_CERTIFICATION_PROFILE_ID = requirement.STUDENT_CERTIFICATION_PROFILE_ID
                    WHERE profile.STUDENT_ID = :studentId
             )
            """, params);
        jdbc.update("""
            DELETE FROM STUDENT_CERT_REQUIREMENT
             WHERE STUDENT_CERTIFICATION_PROFILE_ID IN (
                   SELECT STUDENT_CERTIFICATION_PROFILE_ID
                     FROM STUDENT_CERTIFICATION_PROFILE
                    WHERE STUDENT_ID = :studentId
             )
            """, params);
        jdbc.update("DELETE FROM STUDENT_CERTIFICATION_PROFILE WHERE STUDENT_ID = :studentId", params);
        jdbc.update("DELETE FROM STUDENT_SESSION WHERE STUDENT_ID = :studentId", params);

        int deleted = jdbc.update("""
            DELETE FROM STUDENT
             WHERE STUDENT_ID = :studentId
               AND ORGANIZATION_ID = :organizationId
               AND STATUS = 'DELETED'
            """, params);
        if (deleted != 1) {
            throw new BusinessException("STUDENT_PERMANENT_DELETE_FAILED",
                    "No fue posible completar la eliminación permanente del estudiante.");
        }

        String operationReference = UUID.randomUUID().toString();
        audit.record(actor.userId(), "STUDENT_PERMANENTLY_DELETED", "STUDENTS",
                "Se eliminó permanentemente un estudiante y sus relaciones exclusivas.",
                actor.ipAddress(), actor.userAgent(),
                Map.of("operationReference", operationReference,
                        "organizationId", student.getOrganizationId()), now);
        return new DeletionResult(publicId, operationReference, now);
    }

    public record DeletionResult(String publicId, String operationReference, Instant deletedAt) {}
}
