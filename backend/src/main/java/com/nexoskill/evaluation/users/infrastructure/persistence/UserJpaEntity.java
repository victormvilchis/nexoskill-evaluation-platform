package com.nexoskill.evaluation.users.infrastructure.persistence;

import com.nexoskill.evaluation.users.domain.model.UserStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "APP_USER")
public class UserJpaEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "USER_ID")
	private Long id;

	@Column(name = "PUBLIC_ID", nullable = false, unique = true, length = 36)
	private String publicId;

	@Column(name = "EMAIL", nullable = false, length = 254)
	private String email;

	@Column(name = "NORMALIZED_EMAIL", nullable = false, unique = true, length = 254)
	private String normalizedEmail;

	@Column(name = "PASSWORD_HASH", nullable = false, length = 255)
	private String passwordHash;

	@Column(name = "FIRST_NAME", nullable = false, length = 100)
	private String firstName;

	@Column(name = "LAST_NAME", nullable = false, length = 150)
	private String lastName;

	@Column(name = "DISPLAY_NAME", nullable = false, length = 250)
	private String displayName;

	@Enumerated(EnumType.STRING)
	@Column(name = "STATUS", nullable = false, length = 30)
	private UserStatus status;

	@Column(name = "STATUS_REASON", length = 500)
	private String statusReason;

	@Column(name = "STATUS_CHANGED_AT")
	private Instant statusChangedAt;

	@Column(name = "STATUS_CHANGED_BY")
	private Long statusChangedBy;

	@Column(name = "FAILED_LOGIN_ATTEMPTS", nullable = false)
	private int failedLoginAttempts;

	@Column(name = "LOCKED_UNTIL")
	private Instant lockedUntil;

	@Column(name = "LAST_LOGIN_AT")
	private Instant lastLoginAt;

	@Column(name = "PASSWORD_CHANGE_REQUIRED", nullable = false)
	private Integer passwordChangeRequired;

	@Column(name = "PASSWORD_CHANGED_AT")
	private Instant passwordChangedAt;

	@Column(name = "TEMP_PASSWORD_EXPIRES_AT")
	private Instant temporaryPasswordExpiresAt;

	@Column(name = "DELETED_AT")
	private Instant deletedAt;

	@Column(name = "DELETED_BY")
	private Long deletedBy;

	@Column(name = "DELETE_REASON", length = 500)
	private String deleteReason;

	@Column(name = "CREATED_AT", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "UPDATED_AT")
	private Instant updatedAt;

	@Version
	@Column(name = "VERSION_NO", nullable = false)
	private long version;

	@ManyToMany(fetch = FetchType.EAGER)
	@JoinTable(name = "APP_USER_ROLE", joinColumns = @JoinColumn(name = "USER_ID"), inverseJoinColumns = @JoinColumn(name = "ROLE_ID"))
	private Set<RoleJpaEntity> roles = new LinkedHashSet<>();

	@OneToOne(mappedBy = "user", fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true, optional = false)
	private UserAccessJpaEntity access;

	protected UserJpaEntity() {
	}

	public static UserJpaEntity create(String publicId, String email, String normalizedEmail, String passwordHash,
			String firstName, String lastName, String displayName, RoleJpaEntity role, Instant startsAt,
			Instant expiresAt, UserStatus initialStatus, boolean passwordChangeRequired, Instant passwordChangedAt,
			Instant temporaryPasswordExpiresAt, Long actorUserId, Instant now) {
		var entity = new UserJpaEntity();
		entity.publicId = publicId;
		entity.email = email;
		entity.normalizedEmail = normalizedEmail;
		entity.passwordHash = passwordHash;
		entity.firstName = firstName;
		entity.lastName = lastName;
		entity.displayName = displayName;
		entity.status = initialStatus;
		entity.statusReason = "Creación de usuario";
		entity.statusChangedAt = now;
		entity.statusChangedBy = actorUserId;
		entity.failedLoginAttempts = 0;
		entity.passwordChangeRequired = passwordChangeRequired ? 1 : 0;
		entity.passwordChangedAt = passwordChangedAt;
		entity.temporaryPasswordExpiresAt = temporaryPasswordExpiresAt;
		entity.createdAt = now;
		entity.updatedAt = now;
		entity.roles.add(role);
		entity.access = UserAccessJpaEntity.create(entity, startsAt, expiresAt);
		if (initialStatus != UserStatus.ACTIVE) {
			entity.access.changeStatus(com.nexoskill.evaluation.users.domain.model.UserAccessStatus.SUSPENDED, now);
		}
		return entity;
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = Instant.now();
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

	public String getStatusReason() {
		return statusReason;
	}

	public Instant getStatusChangedAt() {
		return statusChangedAt;
	}

	public Long getStatusChangedBy() {
		return statusChangedBy;
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

	public boolean isPasswordChangeRequired() {
		return Integer.valueOf(1).equals(passwordChangeRequired);
	}

	public Instant getPasswordChangedAt() {
		return passwordChangedAt;
	}

	public Instant getTemporaryPasswordExpiresAt() {
		return temporaryPasswordExpiresAt;
	}

	public Set<RoleJpaEntity> getRoles() {
		return roles;
	}

	public UserAccessJpaEntity getAccess() {
		return access;
	}

	public Instant getDeletedAt() {
		return deletedAt;
	}

	public Long getDeletedBy() {
		return deletedBy;
	}

	public String getDeleteReason() {
		return deleteReason;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void updateProfile(String email, String normalizedEmail, String firstName, String lastName,
			String displayName) {
		this.email = email;
		this.normalizedEmail = normalizedEmail;
		this.firstName = firstName;
		this.lastName = lastName;
		this.displayName = displayName;
	}

	public void replaceRole(RoleJpaEntity role) {
		roles.clear();
		roles.add(role);
	}

	public void changeStatus(UserStatus nextStatus, Long actorUserId, String reason, Instant changedAt) {
		status = nextStatus;
		statusReason = reason;
		statusChangedBy = actorUserId;
		statusChangedAt = changedAt;
		if (nextStatus == UserStatus.ACTIVE) {
			failedLoginAttempts = 0;
			lockedUntil = null;
		}
	}

	public void softDelete(Long actor, Instant now, String reason) {
		changeStatus(UserStatus.DELETED, actor, reason, now);
		deletedAt = now;
		deletedBy = actor;
		deleteReason = reason;
		failedLoginAttempts = 0;
		lockedUntil = null;
	}

	public void restoreAsInactive(Long actor, Instant now, String reason) {
		changeStatus(UserStatus.INACTIVE, actor, reason, now);
		deletedAt = null;
		deletedBy = null;
		deleteReason = null;
		failedLoginAttempts = 0;
		lockedUntil = null;
	}

	public void resetPassword(String encodedPassword, Instant temporaryPasswordExpiresAt) {
		passwordHash = encodedPassword;
		passwordChangeRequired = 1;
		passwordChangedAt = null;
		this.temporaryPasswordExpiresAt = temporaryPasswordExpiresAt;
		failedLoginAttempts = 0;
		lockedUntil = null;
	}

	public void applyAuthenticationState(int failedAttempts, Instant lockedUntil, Instant lastLoginAt) {
		failedLoginAttempts = failedAttempts;
		this.lockedUntil = lockedUntil;
		this.lastLoginAt = lastLoginAt;
	}

	public void applyPasswordState(String encodedPassword, boolean passwordChangeRequired, Instant passwordChangedAt,
			Instant temporaryPasswordExpiresAt) {
		passwordHash = encodedPassword;
		this.passwordChangeRequired = passwordChangeRequired ? 1 : 0;
		this.passwordChangedAt = passwordChangedAt;
		this.temporaryPasswordExpiresAt = temporaryPasswordExpiresAt;
	}
}
