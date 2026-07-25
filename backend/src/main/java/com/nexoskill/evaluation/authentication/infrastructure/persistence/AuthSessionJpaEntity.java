package com.nexoskill.evaluation.authentication.infrastructure.persistence;

import com.nexoskill.evaluation.authentication.domain.model.SessionStatus;
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
@Table(name = "AUTH_SESSION")
public class AuthSessionJpaEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "SESSION_ID")
	private Long id;

	@Column(name = "PUBLIC_ID", nullable = false, unique = true, length = 36)
	private String publicId;

	@Column(name = "USER_ID", nullable = false)
	private Long userId;

	@Column(name = "TOKEN_HASH", nullable = false, unique = true, length = 64)
	private String tokenHash;

	@Enumerated(EnumType.STRING)
	@Column(name = "STATUS", nullable = false, length = 30)
	private SessionStatus status;

	@Column(name = "IP_ADDRESS", length = 64)
	private String ipAddress;

	@Column(name = "USER_AGENT", length = 1000)
	private String userAgent;

	@Column(name = "CREATED_AT", nullable = false)
	private Instant createdAt;

	@Column(name = "LAST_ACTIVITY_AT")
	private Instant lastActivityAt;

	@Column(name = "EXPIRES_AT", nullable = false)
	private Instant expiresAt;

	@Column(name = "REVOKED_AT")
	private Instant revokedAt;

	protected AuthSessionJpaEntity() {
	}

	public static AuthSessionJpaEntity create(String publicId, Long userId, String tokenHash, SessionStatus status,
			String ipAddress, String userAgent, Instant createdAt, Instant lastActivityAt, Instant expiresAt,
			Instant revokedAt) {

		AuthSessionJpaEntity entity = new AuthSessionJpaEntity();
		entity.publicId = publicId;
		entity.userId = userId;
		entity.tokenHash = tokenHash;
		entity.status = status;
		entity.ipAddress = ipAddress;
		entity.userAgent = userAgent;
		entity.createdAt = createdAt;
		entity.lastActivityAt = lastActivityAt;
		entity.expiresAt = expiresAt;
		entity.revokedAt = revokedAt;
		return entity;
	}

	public Long getId() {
		return id;
	}

	public String getPublicId() {
		return publicId;
	}

	public Long getUserId() {
		return userId;
	}

	public String getTokenHash() {
		return tokenHash;
	}

	public SessionStatus getStatus() {
		return status;
	}

	public String getIpAddress() {
		return ipAddress;
	}

	public String getUserAgent() {
		return userAgent;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getLastActivityAt() {
		return lastActivityAt;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public Instant getRevokedAt() {
		return revokedAt;
	}

	public void apply(SessionStatus status, Instant revokedAt) {
		this.status = status;
		this.revokedAt = revokedAt;
	}
}
