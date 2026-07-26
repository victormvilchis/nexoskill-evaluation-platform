package com.nexoskill.evaluation.students.infrastructure.persistence;

import com.nexoskill.evaluation.students.domain.StudentSessionRevocationReason;
import com.nexoskill.evaluation.students.domain.StudentSessionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "STUDENT_SESSION")
public class StudentSessionJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "STUDENT_SESSION_ID")
    private Long id;

    @Column(name = "PUBLIC_ID", nullable = false, unique = true, length = 36)
    private String publicId;

    @Column(name = "STUDENT_ID", nullable = false)
    private Long studentId;

    @Column(name = "ORGANIZATION_ID", nullable = false)
    private Long organizationId;

    @Column(name = "TOKEN_HASH", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 30)
    private StudentSessionStatus status;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "REVOCATION_REASON", length = 80)
    private StudentSessionRevocationReason revocationReason;

    @Version
    @Column(name = "VERSION_NO", nullable = false)
    private Long version;

    protected StudentSessionJpaEntity() {
    }

    public static StudentSessionJpaEntity create(String publicId, Long studentId, Long organizationId,
            String tokenHash, String ipAddress, String userAgent, Instant now, Instant expiresAt) {
        StudentSessionJpaEntity entity = new StudentSessionJpaEntity();
        entity.publicId = publicId;
        entity.studentId = studentId;
        entity.organizationId = organizationId;
        entity.tokenHash = tokenHash;
        entity.status = StudentSessionStatus.ACTIVE;
        entity.ipAddress = ipAddress;
        entity.userAgent = userAgent;
        entity.createdAt = now;
        entity.lastActivityAt = now;
        entity.expiresAt = expiresAt;
        entity.version = 0L;
        return entity;
    }

    public boolean isActiveAt(Instant now) {
        return status == StudentSessionStatus.ACTIVE && expiresAt.isAfter(now);
    }

    public void touch(Instant now) {
        lastActivityAt = now;
    }

    public void revoke(StudentSessionRevocationReason reason, Instant now) {
        status = StudentSessionStatus.REVOKED;
        revokedAt = now;
        revocationReason = reason;
    }

    public void expire(Instant now) {
        status = StudentSessionStatus.EXPIRED;
        revokedAt = now;
        revocationReason = StudentSessionRevocationReason.EXPIRED;
    }

    public Long getId() { return id; }
    public String getPublicId() { return publicId; }
    public Long getStudentId() { return studentId; }
    public Long getOrganizationId() { return organizationId; }
    public String getTokenHash() { return tokenHash; }
    public StudentSessionStatus getStatus() { return status; }
    public String getIpAddress() { return ipAddress; }
    public String getUserAgent() { return userAgent; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastActivityAt() { return lastActivityAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public StudentSessionRevocationReason getRevocationReason() { return revocationReason; }
    public Long getVersion() { return version; }
}
