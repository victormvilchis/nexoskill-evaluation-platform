package com.nexoskill.evaluation.organizations.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "APP_USER_ORGANIZATION")
public class UserOrganizationMembershipJpaEntity {
    @Id
    @Column(name = "USER_ID")
    private Long userId;

    @Column(name = "ORGANIZATION_ID", nullable = false)
    private Long organizationId;

    @Column(name = "STATUS", nullable = false, length = 20)
    private String status;

    @Column(name = "ASSIGNED_AT", nullable = false)
    private Instant assignedAt;

    @Column(name = "ASSIGNED_BY")
    private Long assignedBy;

    protected UserOrganizationMembershipJpaEntity() {
    }

    public static UserOrganizationMembershipJpaEntity active(Long userId, Long organizationId, Long actorUserId,
            Instant now) {
        UserOrganizationMembershipJpaEntity entity = new UserOrganizationMembershipJpaEntity();
        entity.userId = userId;
        entity.organizationId = organizationId;
        entity.status = "ACTIVE";
        entity.assignedAt = now;
        entity.assignedBy = actorUserId;
        return entity;
    }

    public void reassign(Long organizationId, Long actorUserId, Instant now) {
        this.organizationId = organizationId;
        this.status = "ACTIVE";
        this.assignedAt = now;
        this.assignedBy = actorUserId;
    }

    public Long getUserId() { return userId; }
    public Long getOrganizationId() { return organizationId; }
    public String getStatus() { return status; }
}
