package com.nexoskill.evaluation.users.infrastructure.persistence;

import com.nexoskill.evaluation.users.domain.model.UserAccessStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "USER_ACCESS")
public class UserAccessJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "USER_ACCESS_ID")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "USER_ID", nullable = false, unique = true)
    private UserJpaEntity user;

    @Column(name = "STARTS_AT", nullable = false)
    private Instant startsAt;

    @Column(name = "EXPIRES_AT")
    private Instant expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 30)
    private UserAccessStatus status;

    @Column(name = "CREATED_AT", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "UPDATED_AT")
    private Instant updatedAt;

    @Version
    @Column(name = "VERSION_NO", nullable = false)
    private long version;

    protected UserAccessJpaEntity() {
    }

    public static UserAccessJpaEntity create(
            UserJpaEntity user,
            Instant startsAt,
            Instant expiresAt) {
        UserAccessJpaEntity entity = new UserAccessJpaEntity();
        entity.user = user;
        entity.startsAt = startsAt;
        entity.expiresAt = expiresAt;
        entity.status = UserAccessStatus.ACTIVE;
        return entity;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public UserAccessStatus getStatus() {
        return status;
    }
}
