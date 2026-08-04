package com.nexoskill.evaluation.students.application;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.organizations.domain.model.TenantContext;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentPhysicalDeletionService {
	private final NamedParameterJdbcTemplate jdbc;
	private final AuditLogPort audit;
	private final Clock clock;

	public StudentPhysicalDeletionService(NamedParameterJdbcTemplate jdbc, AuditLogPort audit, Clock clock) {
		this.jdbc = jdbc;
		this.audit = audit;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public PageResult search(TenantContext tenant, String query, String module, String organizationPublicId, int page,
			int size) {
		requireTenant(tenant);
		int safePage = Math.max(page, 0);
		int safeSize = Math.min(Math.max(size, 1), 100);
		MapSqlParameterSource params = new MapSqlParameterSource().addValue("offset", safePage * safeSize)
				.addValue("size", safeSize);
		StringBuilder where = new StringBuilder(" WHERE o.ORGANIZATION_TYPE='CUSTOMER' ");
		if (!tenant.globalAdministrator() || !tenant.globalScope()) {
			where.append(" AND s.ORGANIZATION_ID=:organizationId ");
			params.addValue("organizationId", tenant.organizationId());
		} else if (organizationPublicId != null && !organizationPublicId.isBlank()) {
			where.append(" AND o.PUBLIC_ID=:organizationPublicId ");
			params.addValue("organizationPublicId", organizationPublicId.trim());
		}
		if (module != null && !module.isBlank() && !"ALL".equalsIgnoreCase(module)) {
			String normalized = module.trim().toUpperCase();
			if (!List.of("COLLABORATOR", "TALENT_BANK").contains(normalized)) {
				throw new BusinessException("PERMANENT_DELETION_MODULE_INVALID", "El módulo solicitado no es válido.");
			}
			where.append(" AND s.RECORD_MODULE=:recordModule ");
			params.addValue("recordModule", normalized);
		}
		if (query != null && !query.isBlank()) {
			where.append(
					" AND (LOWER(s.DISPLAY_NAME) LIKE :query OR LOWER(s.EMAIL) LIKE :query OR LOWER(s.STUDENT_CODE) LIKE :query) ");
			params.addValue("query", "%" + query.trim().toLowerCase() + "%");
		}
		Long total = jdbc.queryForObject(
				"SELECT COUNT(*) FROM STUDENT s JOIN ORGANIZATION o ON o.ORGANIZATION_ID=s.ORGANIZATION_ID " + where,
				params, Long.class);
		List<Candidate> content = total == null || total == 0 ? List.of()
				: jdbc.query("""
						SELECT s.PUBLIC_ID, s.DISPLAY_NAME, s.EMAIL, s.STATUS, s.RECORD_MODULE, s.TALENT_TYPE,
						       o.PUBLIC_ID ORGANIZATION_PUBLIC_ID, o.ORGANIZATION_CODE, o.ORGANIZATION_NAME,
						       CASE WHEN cv.TALENT_CV_DOCUMENT_ID IS NULL THEN 0 ELSE 1 END HAS_CV
						  FROM STUDENT s JOIN ORGANIZATION o ON o.ORGANIZATION_ID=s.ORGANIZATION_ID
						  LEFT JOIN TALENT_CV_DOCUMENT cv ON cv.STUDENT_ID=s.STUDENT_ID
						""" + where
						+ " ORDER BY LOWER(s.DISPLAY_NAME), s.STUDENT_ID OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY",
						params,
						(rs, rowNum) -> new Candidate(rs.getString("PUBLIC_ID"), rs.getString("DISPLAY_NAME"),
								rs.getString("EMAIL"), rs.getString("STATUS"), rs.getString("RECORD_MODULE"),
								rs.getString("TALENT_TYPE"), rs.getString("ORGANIZATION_PUBLIC_ID"),
								rs.getString("ORGANIZATION_CODE"), rs.getString("ORGANIZATION_NAME"),
								rs.getBoolean("HAS_CV")));
		long safeTotal = total == null ? 0 : total;
		return new PageResult(content, safePage, safeSize, safeTotal,
				safeTotal == 0 ? 0 : (int) Math.ceil((double) safeTotal / safeSize));
	}

	@Transactional
	public DeletionResult delete(TenantContext tenant, String publicId, boolean confirmed, StudentService.Actor actor) {
		if (!confirmed) {
			throw new BusinessException("PERMANENT_DELETION_CONFIRMATION_REQUIRED",
					"Debes confirmar la eliminación física definitiva.");
		}
		requireTenant(tenant);
		MapSqlParameterSource params = new MapSqlParameterSource("publicId", publicId);
		String sql = """
				SELECT s.STUDENT_ID, s.ORGANIZATION_ID, s.RECORD_MODULE, s.DISPLAY_NAME
				  FROM STUDENT s
				  JOIN ORGANIZATION o ON o.ORGANIZATION_ID=s.ORGANIZATION_ID
				 WHERE s.PUBLIC_ID=:publicId
				   AND o.ORGANIZATION_TYPE='CUSTOMER'
				"""
				+ ((!tenant.globalAdministrator() || !tenant.globalScope()) ? " AND s.ORGANIZATION_ID=:organizationId"
						: "")
				+ " FOR UPDATE";
		if (!tenant.globalAdministrator() || !tenant.globalScope())
			params.addValue("organizationId", tenant.organizationId());
		List<StudentRef> rows = jdbc.query(sql, params, (rs, rowNum) -> new StudentRef(rs.getLong("STUDENT_ID"),
				rs.getLong("ORGANIZATION_ID"), rs.getString("RECORD_MODULE"), rs.getString("DISPLAY_NAME")));
		if (rows.isEmpty())
			throw new BusinessException("PERMANENT_DELETION_NOT_FOUND",
					"El registro no existe o no pertenece a tu organización.");
		StudentRef student = rows.getFirst();
		MapSqlParameterSource ids = new MapSqlParameterSource("studentId", student.id()).addValue("publicId", publicId);

		jdbc.update("DELETE FROM STUDENT_CERTIFICATION_HISTORY WHERE STUDENT_ID=:studentId", ids);
		jdbc.update(
				"DELETE FROM STUDENT_CERTIFICATION_CYCLE_ATTEMPT WHERE STUDENT_CERTIFICATION_CYCLE_ID IN (SELECT STUDENT_CERTIFICATION_CYCLE_ID FROM STUDENT_CERTIFICATION_CYCLE WHERE STUDENT_ID=:studentId)",
				ids);
		jdbc.update(
				"DELETE FROM STUDENT_CERTIFICATION_ATTEMPT WHERE STUDENT_CERT_REQUIREMENT_ID IN (SELECT r.STUDENT_CERT_REQUIREMENT_ID FROM STUDENT_CERT_REQUIREMENT r JOIN STUDENT_CERTIFICATION_PROFILE p ON p.STUDENT_CERTIFICATION_PROFILE_ID=r.STUDENT_CERTIFICATION_PROFILE_ID WHERE p.STUDENT_ID=:studentId)",
				ids);
		jdbc.update("DELETE FROM STUDENT_CERTIFICATION_CYCLE WHERE STUDENT_ID=:studentId", ids);
		jdbc.update(
				"DELETE FROM STUDENT_CERT_REQUIREMENT WHERE STUDENT_CERTIFICATION_PROFILE_ID IN (SELECT STUDENT_CERTIFICATION_PROFILE_ID FROM STUDENT_CERTIFICATION_PROFILE WHERE STUDENT_ID=:studentId)",
				ids);
		jdbc.update("DELETE FROM STUDENT_CERTIFICATION_PROFILE WHERE STUDENT_ID=:studentId", ids);
		jdbc.update("DELETE FROM STUDENT_IMPORT_CONFLICT_DECISION WHERE STUDENT_ID=:studentId", ids);
		jdbc.update("DELETE FROM STUDENT_EXPERIENCE_ITEM WHERE STUDENT_ID=:studentId", ids);
		jdbc.update("DELETE FROM STUDENT_SESSION WHERE STUDENT_ID=:studentId", ids);
		jdbc.update("DELETE FROM TALENT_CV_DOCUMENT WHERE STUDENT_ID=:studentId", ids);
		jdbc.update(
				"DELETE FROM AUDIT_EVENT WHERE EVENT_DATA IS NOT NULL AND DBMS_LOB.INSTR(EVENT_DATA, :publicId) > 0",
				ids);
		int deleted = jdbc.update("DELETE FROM STUDENT WHERE STUDENT_ID=:studentId", ids);
		if (deleted != 1)
			throw new BusinessException("PERMANENT_DELETION_FAILED", "No fue posible eliminar el registro completo.");

		String reference = UUID.randomUUID().toString();
		Instant now = clock.instant();
		audit.record(actor.userId(), "PERSON_RECORD_PERMANENTLY_DELETED", "ADMINISTRATION",
				"Un registro de persona fue eliminado definitivamente.", actor.ipAddress(), actor.userAgent(),
				Map.of("operationReference", reference, "organizationId", student.organizationId(), "sourceModule",
						student.module()),
				now);
		return new DeletionResult(reference, now);
	}

	private void requireTenant(TenantContext tenant) {
		if (tenant == null || (!tenant.globalAdministrator() && !tenant.hasOrganization())) {
			throw new BusinessException("ORGANIZATION_CONTEXT_REQUIRED", "No existe un contexto autorizado.");
		}
	}

	private record StudentRef(Long id, Long organizationId, String module, String displayName) {
	}

	public record Candidate(String publicId, String displayName, String email, String status, String module,
			String talentType, String organizationPublicId, String organizationCode, String organizationName,
			boolean hasCv) {
	}

	public record PageResult(List<Candidate> content, int page, int size, long totalElements, int totalPages) {
		public PageResult {
			content = content == null ? List.of() : List.copyOf(content);
		}
	}

	public record DeletionResult(String operationReference, Instant deletedAt) {
	}
}
