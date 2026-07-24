package com.nexoskill.evaluation.authentication.domain.model;

import java.time.Instant;

public class AuthSession {

    private final Long id;
    private final String publicId;
    private final Long userId;
    private final String tokenHash;
    private SessionStatus status;
    private final String ipAddress;
    private final String userAgent;
    private final Instant createdAt;
    private Instant lastActivityAt;
    private final Instant expiresAt;
    private Instant revokedAt;

    public AuthSession(
            Long id,
            String publicId,
            Long userId,
            String tokenHash,
            SessionStatus status,
            String ipAddress,
            String userAgent,
            Instant createdAt,
            Instant lastActivityAt,
            Instant expiresAt,
            Instant revokedAt) {
        this.id = id;
        this.publicId = publicId;
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.status = status;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.createdAt = createdAt;
        this.lastActivityAt = lastActivityAt;
        this.expiresAt = expiresAt;
        this.revokedAt = revokedAt;
    }

    public static AuthSession create(
            String publicId,
            Long userId,
            String tokenHash,
            String ipAddress,
            String userAgent,
            Instant now,
            Instant expiresAt) {
        return new AuthSession(
                null,
                publicId,
                userId,
                tokenHash,
                SessionStatus.ACTIVE,
                ipAddress,
                userAgent,
                now,
                now,
                expiresAt,
                null
        );
    }

    public boolean isActiveAt(Instant now) {
        return status == SessionStatus.ACTIVE && expiresAt.isAfter(now);
    }

    public void revoke(Instant now) {
        status = SessionStatus.REVOKED;
        revokedAt = now;
    }

    public void expire() {
        status = SessionStatus.EXPIRED;
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
}
