package com.nexoskill.evaluation.users.domain.model;

import java.time.Instant;
import java.util.Collections;
import java.util.Set;

public class UserAccount {

    private final Long id;
    private final String publicId;
    private final String email;
    private final String normalizedEmail;
    private String passwordHash;
    private final String firstName;
    private final String lastName;
    private final String displayName;
    private UserStatus status;
    private int failedLoginAttempts;
    private Instant lockedUntil;
    private Instant lastLoginAt;
    private final Set<RoleGrant> roles;
    private final UserAccess access;

    public UserAccount(
            Long id,
            String publicId,
            String email,
            String normalizedEmail,
            String passwordHash,
            String firstName,
            String lastName,
            String displayName,
            UserStatus status,
            int failedLoginAttempts,
            Instant lockedUntil,
            Instant lastLoginAt,
            Set<RoleGrant> roles,
            UserAccess access) {

        this.id = id;
        this.publicId = publicId;
        this.email = email;
        this.normalizedEmail = normalizedEmail;
        this.passwordHash = passwordHash;
        this.firstName = firstName;
        this.lastName = lastName;
        this.displayName = displayName;
        this.status = status;
        this.failedLoginAttempts = failedLoginAttempts;
        this.lockedUntil = lockedUntil;
        this.lastLoginAt = lastLoginAt;
        this.roles = Set.copyOf(roles);
        this.access = access;
    }

    public boolean canAuthenticateAt(Instant now) {
        if (status != UserStatus.ACTIVE) {
            return false;
        }
        boolean accountUnlocked = lockedUntil == null || !lockedUntil.isAfter(now);
        return accountUnlocked && access != null && access.isActiveAt(now);
    }

    public void registerFailedLogin(int maximumAttempts, Instant lockUntil) {
        failedLoginAttempts++;
        if (failedLoginAttempts >= maximumAttempts) {
            this.lockedUntil = lockUntil;
        }
    }

    public void registerSuccessfulLogin(Instant now) {
        failedLoginAttempts = 0;
        lockedUntil = null;
        lastLoginAt = now;
    }

    public boolean hasRole(String roleCode) {
        return roles.stream().anyMatch(role -> role.code().equals(roleCode));
    }

    public Set<String> permissions() {
        return roles.stream()
                .flatMap(role -> role.permissions().stream())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public Long getId() {
        return id;
    }

    public String getPublicId() {
        return publicId;
    }

    public String getEmail() {
        return email;
    }

    public String getNormalizedEmail() {
        return normalizedEmail;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public UserStatus getStatus() {
        return status;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public Set<RoleGrant> getRoles() {
        return Collections.unmodifiableSet(roles);
    }

    public UserAccess getAccess() {
        return access;
    }
}
