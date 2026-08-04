package com.nexoskill.evaluation.students.infrastructure.persistence;

import com.nexoskill.evaluation.shared.domain.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reserva y cierra confirmaciones de importación en una transacción
 * independiente. El token persistente evita la doble aplicación incluso entre
 * procesos distintos.
 */
@Component
public class StudentImportReceiptStore {
	private final NamedParameterJdbcTemplate jdbc;
	private final Clock clock;

	public StudentImportReceiptStore(NamedParameterJdbcTemplate jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void reserve(String confirmationToken, Long organizationId, String fileSha256, int totalRows,
			Long actorUserId) {
		try {
			int inserted = jdbc.update("""
					INSERT INTO STUDENT_IMPORT_RECEIPT (
					    PUBLIC_ID, CONFIRMATION_TOKEN, ORGANIZATION_ID, FILE_SHA256, TOTAL_ROWS,
					    CREATED_COUNT, UPDATED_COUNT, DEACTIVATED_COUNT, ERROR_COUNT,
					    STATUS_CODE, APPLIED_BY, APPLIED_AT
					) VALUES (
					    :publicId, :confirmationToken, :organizationId, :fileSha256, :totalRows,
					    0, 0, 0, 0, 'PROCESSING', :appliedBy, :appliedAt
					)
					""",
					new MapSqlParameterSource().addValue("publicId", UUID.randomUUID().toString())
							.addValue("confirmationToken", confirmationToken).addValue("organizationId", organizationId)
							.addValue("fileSha256", fileSha256).addValue("totalRows", totalRows)
							.addValue("appliedBy", actorUserId)
							.addValue("appliedAt", java.sql.Timestamp.from(clock.instant())));
			if (inserted != 1) {
				throw new IllegalStateException("No fue posible reservar la confirmación de importación.");
			}
		} catch (DataIntegrityViolationException exception) {
			throw new BusinessException("STUDENT_IMPORT_ALREADY_APPLIED",
					"Esta vista previa ya está siendo procesada o fue confirmada anteriormente.");
		}
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void complete(String confirmationToken, int created, int updated, int deactivated, int errors) {
		int affected = jdbc.update("""
				UPDATE STUDENT_IMPORT_RECEIPT
				   SET CREATED_COUNT = :created,
				       UPDATED_COUNT = :updated,
				       DEACTIVATED_COUNT = :deactivated,
				       ERROR_COUNT = :errors,
				       STATUS_CODE = 'COMPLETED',
				       COMPLETED_AT = :completedAt,
				       FAILURE_CODE = NULL,
				       FAILED_AT = NULL
				 WHERE CONFIRMATION_TOKEN = :confirmationToken
				   AND STATUS_CODE = 'PROCESSING'
				""",
				new MapSqlParameterSource().addValue("created", created).addValue("updated", updated)
						.addValue("deactivated", deactivated).addValue("errors", errors)
						.addValue("completedAt", java.sql.Timestamp.from(clock.instant()))
						.addValue("confirmationToken", confirmationToken));
		if (affected != 1) {
			throw new IllegalStateException("No fue posible cerrar el comprobante de importación.");
		}
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void fail(String confirmationToken, String failureCode) {
		int affected = jdbc.update("""
				UPDATE STUDENT_IMPORT_RECEIPT
				   SET STATUS_CODE = 'FAILED',
				       FAILURE_CODE = :failureCode,
				       FAILED_AT = :failedAt,
				       COMPLETED_AT = :failedAt
				 WHERE CONFIRMATION_TOKEN = :confirmationToken
				   AND STATUS_CODE = 'PROCESSING'
				""",
				new MapSqlParameterSource().addValue("failureCode", truncate(failureCode, 80))
						.addValue("failedAt", java.sql.Timestamp.from(clock.instant()))
						.addValue("confirmationToken", confirmationToken));
		if (affected > 1) {
			throw new IllegalStateException("Se actualizaron varios comprobantes para la misma confirmación.");
		}
	}

	@Transactional(readOnly = true)
	public String status(String confirmationToken, Long organizationId, Long actorUserId) {
		List<String> statuses = jdbc.query("""
				SELECT STATUS_CODE
				  FROM STUDENT_IMPORT_RECEIPT
				 WHERE CONFIRMATION_TOKEN = :confirmationToken
				   AND ORGANIZATION_ID = :organizationId
				   AND APPLIED_BY = :actorUserId
				""", Map.of("confirmationToken", confirmationToken, "organizationId", organizationId, "actorUserId",
				actorUserId), (resultSet, rowNumber) -> resultSet.getString("STATUS_CODE"));
		return statuses.isEmpty() ? null : statuses.getFirst();
	}

	private String truncate(String value, int maxLength) {
		if (value == null || value.isBlank())
			return "STUDENT_IMPORT_UNEXPECTED_ERROR";
		String normalized = value.trim();
		return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
	}
}
