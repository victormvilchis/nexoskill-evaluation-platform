package com.nexoskill.evaluation.users.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "PASSWORD_HISTORY")
public class PasswordHistoryJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PASSWORD_HISTORY_ID")
    private Long id;

    @Column(name = "USER_ID", nullable = false)
    private Long userId;

    @Column(name = "PASSWORD_HASH", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;

    protected PasswordHistoryJpaEntity() {
    }

    public static PasswordHistoryJpaEntity create(
            Long userId,
            String passwordHash,
            Instant createdAt) {
        PasswordHistoryJpaEntity entity = new PasswordHistoryJpaEntity();
        entity.userId = userId;
        entity.passwordHash = passwordHash;
        entity.createdAt = createdAt;
        return entity;
    }

    public String getPasswordHash() {
        return passwordHash;
    }
}
