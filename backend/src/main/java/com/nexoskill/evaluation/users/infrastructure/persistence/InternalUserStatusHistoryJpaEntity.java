package com.nexoskill.evaluation.users.infrastructure.persistence;

import com.nexoskill.evaluation.users.domain.model.UserStatus;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "INTERNAL_USER_STATUS_HISTORY")
public class InternalUserStatusHistoryJpaEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "STATUS_HISTORY_ID")
	private Long id;

	@Column(name = "USER_ID", nullable = false)
	private Long userId;

	@Column(name = "ACTOR_USER_ID")
	private Long actorUserId;

	@Enumerated(EnumType.STRING)
	@Column(name = "PREVIOUS_STATUS", length = 30)
	private UserStatus previousStatus;

	@Enumerated(EnumType.STRING)
	@Column(name = "NEW_STATUS", nullable = false, length = 30)
	private UserStatus newStatus;

	@Column(name = "REASON", length = 500)
	private String reason;

	@Column(name = "OCCURRED_AT", nullable = false)
	private Instant occurredAt;

	protected InternalUserStatusHistoryJpaEntity() {
	}

	public static InternalUserStatusHistoryJpaEntity create(Long userId, Long actorUserId, UserStatus previousStatus,
			UserStatus newStatus, String reason, Instant occurredAt) {
		InternalUserStatusHistoryJpaEntity entity = new InternalUserStatusHistoryJpaEntity();
		entity.userId = userId;
		entity.actorUserId = actorUserId;
		entity.previousStatus = previousStatus;
		entity.newStatus = newStatus;
		entity.reason = reason;
		entity.occurredAt = occurredAt;
		return entity;
	}

	public Long getActorUserId() {
		return actorUserId;
	}

	public UserStatus getPreviousStatus() {
		return previousStatus;
	}

	public UserStatus getNewStatus() {
		return newStatus;
	}

	public String getReason() {
		return reason;
	}

	public Instant getOccurredAt() {
		return occurredAt;
	}
}
