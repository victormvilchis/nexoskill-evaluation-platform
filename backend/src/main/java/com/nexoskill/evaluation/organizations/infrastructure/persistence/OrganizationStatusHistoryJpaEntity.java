package com.nexoskill.evaluation.organizations.infrastructure.persistence;

import com.nexoskill.evaluation.organizations.domain.model.OrganizationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "ORGANIZATION_STATUS_HISTORY")
public class OrganizationStatusHistoryJpaEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "HISTORY_ID")
	private Long id;

	@Column(name = "ORGANIZATION_ID", nullable = false)
	private Long organizationId;

	@Enumerated(EnumType.STRING)
	@Column(name = "PREVIOUS_STATUS", length = 20)
	private OrganizationStatus previousStatus;

	@Enumerated(EnumType.STRING)
	@Column(name = "NEW_STATUS", nullable = false, length = 20)
	private OrganizationStatus newStatus;

	@Column(name = "CHANGE_REASON", length = 500)
	private String reason;

	@Column(name = "CHANGED_BY")
	private Long changedBy;

	@Column(name = "CHANGED_AT", nullable = false)
	private Instant changedAt;

	protected OrganizationStatusHistoryJpaEntity() {
	}

	public static OrganizationStatusHistoryJpaEntity create(Long organizationId, OrganizationStatus previousStatus,
			OrganizationStatus newStatus, String reason, Long changedBy, Instant changedAt) {
		OrganizationStatusHistoryJpaEntity entity = new OrganizationStatusHistoryJpaEntity();
		entity.organizationId = organizationId;
		entity.previousStatus = previousStatus;
		entity.newStatus = newStatus;
		entity.reason = reason == null || reason.isBlank() ? null
				: reason.trim().substring(0, Math.min(reason.trim().length(), 500));
		entity.changedBy = changedBy;
		entity.changedAt = changedAt;
		return entity;
	}

	public Long getId() {
		return id;
	}

	public Long getOrganizationId() {
		return organizationId;
	}

	public OrganizationStatus getPreviousStatus() {
		return previousStatus;
	}

	public OrganizationStatus getNewStatus() {
		return newStatus;
	}

	public String getReason() {
		return reason;
	}

	public Long getChangedBy() {
		return changedBy;
	}

	public Instant getChangedAt() {
		return changedAt;
	}
}
