package com.nexoskill.evaluation.users.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "APP_ROLE")
public class RoleJpaEntity {

    public static final String ADMINISTRATOR_CODE = "ADMINISTRATOR";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ROLE_ID")
    private Long id;

    @Column(name = "ROLE_CODE", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "ROLE_NAME", nullable = false, length = 100)
    private String name;

    @Column(name = "DESCRIPTION", length = 500)
    private String description;

    @Column(name = "SYSTEM_ROLE", nullable = false)
    private Integer systemRole;

    @Column(name = "STATUS", nullable = false, length = 20)
    private String status;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "UPDATED_AT")
    private Instant updatedAt;

    @Version
    @Column(name = "VERSION_NO", nullable = false)
    private long version;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "APP_ROLE_PERMISSION", joinColumns = @JoinColumn(name = "ROLE_ID"), inverseJoinColumns = @JoinColumn(name = "PERMISSION_ID"))
    private Set<PermissionJpaEntity> permissions = new LinkedHashSet<>();

    protected RoleJpaEntity() {
    }

    public static RoleJpaEntity custom(String code, String name, String description,
            Collection<PermissionJpaEntity> permissions, Instant now) {
        RoleJpaEntity entity = new RoleJpaEntity();
        entity.code = code;
        entity.name = name;
        entity.description = description;
        entity.systemRole = 0;
        entity.status = "ACTIVE";
        entity.createdAt = now;
        entity.updatedAt = now;
        entity.permissions.addAll(permissions);
        return entity;
    }


    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getStatus() {
        return status;
    }

    public boolean isSystemRole() {
        return Integer.valueOf(1).equals(systemRole);
    }

    public boolean isProtectedAdministrator() {
        return code != null && ADMINISTRATOR_CODE.equalsIgnoreCase(code.trim());
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }

    public Set<PermissionJpaEntity> getPermissions() {
        return permissions;
    }

    public void update(String name, String description, Collection<PermissionJpaEntity> nextPermissions,
            Instant changedAt) {
        this.name = name;
        this.description = description;
        permissions.clear();
        permissions.addAll(nextPermissions);
        updatedAt = changedAt;
    }

    public void changeStatus(String nextStatus, Instant changedAt) {
        status = nextStatus;
        updatedAt = changedAt;
    }
}
