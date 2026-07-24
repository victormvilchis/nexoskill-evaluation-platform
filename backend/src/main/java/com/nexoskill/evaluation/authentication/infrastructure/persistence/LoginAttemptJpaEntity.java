package com.nexoskill.evaluation.authentication.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "LOGIN_ATTEMPT")
public class LoginAttemptJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "LOGIN_ATTEMPT_ID")
    private Long id;

    @Column(name = "USER_ID")
    private Long userId;

    @Column(name = "ATTEMPTED_EMAIL", length = 254)
    private String attemptedEmail;

    @Column(name = "LOGIN_SUCCEEDED", nullable = false)
    private Integer successful;

    @Column(name = "FAILURE_REASON", length = 100)
    private String failureReason;

    @Column(name = "IP_ADDRESS", length = 64)
    private String ipAddress;

    @Column(name = "USER_AGENT", length = 1000)
    private String userAgent;

    @Column(name = "ATTEMPTED_AT", nullable = false)
    private Instant attemptedAt;

    protected LoginAttemptJpaEntity() {
    }

    public static LoginAttemptJpaEntity create(
            Long userId,
            String attemptedEmail,
            boolean successful,
            String failureReason,
            String ipAddress,
            String userAgent,
            Instant attemptedAt) {
        LoginAttemptJpaEntity entity = new LoginAttemptJpaEntity();
        entity.userId = userId;
        entity.attemptedEmail = attemptedEmail;
        entity.successful = successful ? 1 : 0;
        entity.failureReason = failureReason;
        entity.ipAddress = ipAddress;
        entity.userAgent = userAgent;
        entity.attemptedAt = attemptedAt;
        return entity;
    }
}

