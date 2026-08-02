package com.nexoskill.evaluation.students.application;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.organizations.domain.model.TenantContext;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import com.nexoskill.evaluation.students.domain.StudentSessionRevocationReason;
import com.nexoskill.evaluation.students.domain.StudentSessionStatus;
import com.nexoskill.evaluation.students.domain.StudentStatus;
import com.nexoskill.evaluation.students.infrastructure.persistence.StudentJpaEntity;
import com.nexoskill.evaluation.students.infrastructure.persistence.StudentRepository;
import com.nexoskill.evaluation.students.infrastructure.persistence.StudentSessionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Eliminación lógica y transaccional de un colaborador, sin destruir sus históricos. */
@Service
public class StudentDeletionService {
    private final StudentRepository students;
    private final StudentSessionRepository sessions;
    private final AuditLogPort audit;
    private final Clock clock;

    public StudentDeletionService(StudentRepository students, StudentSessionRepository sessions,
            AuditLogPort audit, Clock clock) {
        this.students = students;
        this.sessions = sessions;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * Conserva el nombre por compatibilidad con clientes anteriores. La operación es exclusivamente
     * lógica: bloquea la cuenta, revoca sesiones y mantiene certificaciones, resultados e históricos.
     */
    @Transactional
    public DeletionResult deletePermanently(TenantContext tenant, String publicId, boolean confirmed,
            StudentService.Actor actor) {
        if (!confirmed) {
            throw new BusinessException("STUDENT_DELETE_CONFIRMATION_REQUIRED",
                    "Debes confirmar la eliminación del colaborador.",
                    Map.of("confirmed", "Confirma la eliminación lógica para continuar."));
        }
        if (tenant == null || !tenant.hasOrganization() || tenant.globalScope() || tenant.globalAdministrator()) {
            throw new BusinessException("STUDENT_DELETE_FORBIDDEN",
                    "No existe un contexto organizacional autorizado para eliminar al colaborador.");
        }
        StudentJpaEntity student = students.findByOrganizationIdAndPublicIdForUpdate(tenant.organizationId(), publicId)
                .orElseThrow(() -> new BusinessException("STUDENT_NOT_FOUND", "El colaborador no existe."));
        if (!student.getOrganizationId().equals(tenant.organizationId())) {
            throw new BusinessException("STUDENT_DELETE_FORBIDDEN",
                    "No tienes permisos para eliminar colaboradores de otra organización.");
        }
        if (student.getStatus() == StudentStatus.DELETED) {
            throw new BusinessException("STUDENT_STATUS_UNCHANGED", "El colaborador ya fue eliminado.");
        }

        Instant now = clock.instant();
        student.softDelete(actor.userId(), now, "ELIMINACIÓN LÓGICA DESDE ADMINISTRACIÓN");
        students.saveAndFlush(student);
        sessions.revokeActive(student.getId(), StudentSessionStatus.ACTIVE, StudentSessionStatus.REVOKED,
                StudentSessionRevocationReason.DELETED, now);

        String operationReference = UUID.randomUUID().toString();
        audit.record(actor.userId(), "STUDENT_DELETED", "STUDENTS",
                "Se eliminó lógicamente un colaborador y se conservaron sus históricos.",
                actor.ipAddress(), actor.userAgent(),
                Map.of("operationReference", operationReference,
                        "organizationId", student.getOrganizationId(),
                        "studentPublicId", student.getPublicId(),
                        "historicalDataPreserved", true), now);
        return new DeletionResult(publicId, operationReference, now);
    }

    public record DeletionResult(String publicId, String operationReference, Instant deletedAt) {}
}
