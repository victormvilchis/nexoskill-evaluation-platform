package com.nexoskill.evaluation.students.application;

import com.nexoskill.evaluation.organizations.domain.model.TenantContext;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationLicensePolicyJpaEntity;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationLicensePolicyRepository;
import com.nexoskill.evaluation.students.domain.StudentSessionStatus;
import com.nexoskill.evaluation.students.infrastructure.persistence.StudentJpaEntity;
import com.nexoskill.evaluation.students.infrastructure.persistence.StudentSessionRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentAdministrationService {
	private final StudentService students;
	private final StudentFoundationService foundation;
	private final StudentSessionRepository sessions;
	private final OrganizationLicensePolicyRepository licensePolicies;
	private final NamedParameterJdbcTemplate jdbc;

	public StudentAdministrationService(StudentService students, StudentFoundationService foundation,
			StudentSessionRepository sessions, OrganizationLicensePolicyRepository licensePolicies,
			NamedParameterJdbcTemplate jdbc) {
		this.students = students;
		this.foundation = foundation;
		this.sessions = sessions;
		this.licensePolicies = licensePolicies;
		this.jdbc = jdbc;
	}

	@Transactional(readOnly = true)
	public AdministrationView get(TenantContext tenant, String publicId) {
		StudentJpaEntity student = students.findScopedEntity(tenant, publicId);
		StudentFoundationService.StudentView detail = foundation.get(tenant, publicId);
		List<StudentService.SessionView> sessionViews = students.sessions(tenant, publicId);
		long activeSessions = sessionViews.stream().filter(s -> s.status() == StudentSessionStatus.ACTIVE).count();
		OrganizationLicensePolicyJpaEntity policy = licensePolicies.findByOrganizationId(student.getOrganizationId())
				.orElse(null);
		AdministrativeHistoryItem latest = history(tenant, publicId, 0, 1).content().stream().findFirst().orElse(null);
		return new AdministrationView(detail,
				new SeatView(policy == null ? null : policy.getContractedSeats(), null, "SIN_ASIGNACION_REGISTRADA"),
				activeSessions, sessionViews, latest);
	}

	@Transactional(readOnly = true)
	public HistoryPage history(TenantContext tenant, String publicId, int page, int size) {
		students.findScopedEntity(tenant, publicId);
		int safePage = Math.max(page, 0);
		int safeSize = Math.min(Math.max(size, 1), 100);
		MapSqlParameterSource params = new MapSqlParameterSource().addValue("studentPublicId", publicId)
				.addValue("offset", safePage * safeSize).addValue("size", safeSize);
		String where = " WHERE MODULE_CODE IN ('STUDENTS','STUDENT_CERTIFICATIONS','TALENT_BANK') "
				+ "AND EVENT_TYPE IN ('STUDENT_CREATED','STUDENT_UPDATED','STUDENT_FOUNDATION_CREATED',"
				+ "'STUDENT_FOUNDATION_UPDATED','STUDENT_ACTIVATED','STUDENT_MOVED_TO_TALENT_BANK',"
				+ "'STUDENT_PASSWORD_RESET','STUDENT_SESSION_REVOKED','STUDENT_SESSIONS_REVOKED',"
				+ "'STUDENT_CERTIFICATION_APPLICABILITY_CHANGED',"
				+ "'STUDENT_CERTIFICATIONS_UPDATED','STUDENT_CERTIFICATION_CYCLE_CREATED',"
				+ "'STUDENT_CERTIFICATION_CYCLE_UPDATED','STUDENT_CERTIFICATION_PRIMARY_CHANGED',"
				+ "'STUDENT_CERTIFICATION_CYCLE_CANCELLED','STUDENT_CERTIFICATION_ATTEMPT_CREATED',"
				+ "'STUDENT_CERTIFICATION_ATTEMPT_UPDATED','TALENT_ACADEMY_CREATED','TALENT_PROSPECT_CREATED',"
				+ "'TALENT_UPDATED','TALENT_FOUNDATION_UPDATED','TALENT_CV_UPDATED','TALENT_CONVERTED_TO_STUDENT') "
				+ "AND DBMS_LOB.INSTR(EVENT_DATA, :studentPublicId) > 0 ";
		Long total = jdbc.queryForObject("SELECT COUNT(*) FROM AUDIT_EVENT" + where, params, Long.class);
		List<AdministrativeHistoryItem> content = jdbc.query("""
				SELECT PUBLIC_ID, EVENT_TYPE, DESCRIPTION, USER_ID, OCCURRED_AT
				  FROM AUDIT_EVENT
				""" + where + " ORDER BY OCCURRED_AT DESC, AUDIT_EVENT_ID DESC "
				+ "OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY", params, (rs, rowNum) -> {
					Timestamp occurredAt = rs.getTimestamp("OCCURRED_AT");
					long userId = rs.getLong("USER_ID");
					boolean userIdWasNull = rs.wasNull();
					String eventType = rs.getString("EVENT_TYPE");
					return new AdministrativeHistoryItem(rs.getString("PUBLIC_ID"), historyLabel(eventType),
							rs.getString("DESCRIPTION"), userIdWasNull ? null : userId,
							occurredAt == null ? null : occurredAt.toInstant());
				});
		long safeTotal = total == null ? 0 : total;
		return new HistoryPage(content, safePage, safeSize, safeTotal,
				safeTotal == 0 ? 0 : (int) Math.ceil((double) safeTotal / safeSize));
	}

	private String historyLabel(String eventType) {
		return switch (eventType == null ? "" : eventType) {
		case "STUDENT_CREATED" -> "Alta de colaborador";
		case "STUDENT_UPDATED", "STUDENT_FOUNDATION_UPDATED" -> "Actualización de colaborador";
		case "STUDENT_FOUNDATION_CREATED" -> "Configuración inicial";
		case "STUDENT_ACTIVATED" -> "Activación";
		case "STUDENT_MOVED_TO_TALENT_BANK" -> "Baja";
		case "STUDENT_PASSWORD_RESET" -> "Restablecimiento de contraseña";
		case "STUDENT_SESSION_REVOKED", "STUDENT_SESSIONS_REVOKED" -> "Revocación de acceso";
		case "STUDENT_CERTIFICATION_APPLICABILITY_CHANGED" -> "Cambio de aplicabilidad";
		case "STUDENT_CERTIFICATIONS_UPDATED" -> "Actualización de certificaciones";
		case "STUDENT_CERTIFICATION_CYCLE_CREATED" -> "Alta de ciclo de certificación";
		case "STUDENT_CERTIFICATION_CYCLE_UPDATED" -> "Actualización de ciclo";
		case "STUDENT_CERTIFICATION_PRIMARY_CHANGED" -> "Cambio de certificación principal";
		case "STUDENT_CERTIFICATION_CYCLE_CANCELLED" -> "Cancelación de ciclo";
		case "STUDENT_CERTIFICATION_ATTEMPT_CREATED" -> "Registro de intento";
		case "STUDENT_CERTIFICATION_ATTEMPT_UPDATED" -> "Actualización de intento";
		case "TALENT_ACADEMY_CREATED" -> "Alta en Academia";
		case "TALENT_PROSPECT_CREATED" -> "Alta de prospecto";
		case "TALENT_UPDATED", "TALENT_FOUNDATION_UPDATED" -> "Actualización de talento";
		case "TALENT_CV_UPDATED" -> "Actualización de CV";
		case "TALENT_CONVERTED_TO_STUDENT" -> "Conversión a colaborador";
		default -> "Cambio relevante";
		};
	}

	public record AdministrationView(StudentFoundationService.StudentView student, SeatView seat, long activeSessions,
			List<StudentService.SessionView> sessions, AdministrativeHistoryItem lastAdministrativeChange) {
	}

	public record SeatView(Integer contractedSeats, String assignedSeatPublicId, String status) {
	}

	public record AdministrativeHistoryItem(String publicId, String eventType, String description, Long actorUserId,
			Instant occurredAt) {
	}

	public record HistoryPage(List<AdministrativeHistoryItem> content, int page, int size, long totalElements,
			int totalPages) {
		public HistoryPage {
			content = content == null ? List.of() : List.copyOf(content);
		}
	}
}
