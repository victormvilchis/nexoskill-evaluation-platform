package com.nexoskill.evaluation.students.infrastructure.persistence;

import com.nexoskill.evaluation.students.domain.StudentEffectiveStatus;
import com.nexoskill.evaluation.students.domain.StudentStatus;
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
@Table(name = "STUDENT")
public class StudentJpaEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "STUDENT_ID")
	private Long id;

	@Column(name = "PUBLIC_ID", nullable = false, unique = true, length = 36)
	private String publicId;

	@Column(name = "ORGANIZATION_ID", nullable = false)
	private Long organizationId;

	@Column(name = "STUDENT_CODE", nullable = false, length = 80)
	private String studentCode;

	@Column(name = "EMAIL", nullable = false, length = 254)
	private String email;

	@Column(name = "NORMALIZED_EMAIL", nullable = false, length = 254)
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
	private StudentStatus status;

	@Column(name = "VALID_FROM", nullable = false)
	private Instant validFrom;

	@Column(name = "EXPIRES_AT")
	private Instant expiresAt;

	@Column(name = "FAILED_LOGIN_ATTEMPTS", nullable = false)
	private int failedLoginAttempts;

	@Column(name = "LOCKED_UNTIL")
	private Instant lockedUntil;

	@Column(name = "LAST_LOGIN_AT")
	private Instant lastLoginAt;

	@Column(name = "PASSWORD_CHANGED_AT")
	private Instant passwordChangedAt;

	@Column(name = "PASSWORD_CHANGE_REQUIRED", nullable = false)
	private boolean passwordChangeRequired;

	@Column(name = "TEMP_PASSWORD_EXPIRES_AT")
	private Instant temporaryPasswordExpiresAt;

	@Column(name = "ARCHIVED_AT")
	private Instant archivedAt;

	@Column(name = "DELETED_AT")
	private Instant deletedAt;

	@Column(name = "DELETED_BY")
	private Long deletedBy;

	@Column(name = "DELETION_REASON", length = 500)
	private String deletionReason;

	@Column(name = "CREATED_BY", nullable = false)
	private Long createdBy;

	@Column(name = "UPDATED_BY")
	private Long updatedBy;

	@Column(name = "CREATED_AT", nullable = false)
	private Instant createdAt;

	@Column(name = "UPDATED_AT", nullable = false)
	private Instant updatedAt;

	@Version
	@Column(name = "VERSION_NO", nullable = false)
	private Long version;

	protected StudentJpaEntity() {
	}

	public static StudentJpaEntity create(String publicId, Long organizationId, String studentCode, String email,
			String normalizedEmail, String passwordHash, String firstName, String lastName, String displayName,
			StudentStatus status, Instant validFrom, Instant expiresAt, Instant temporaryPasswordExpiresAt,
			Long actorId, Instant now) {
		StudentJpaEntity entity = new StudentJpaEntity();
		entity.publicId = publicId;
		entity.organizationId = organizationId;
		entity.studentCode = studentCode;
		entity.email = email;
		entity.normalizedEmail = normalizedEmail;
		entity.passwordHash = passwordHash;
		entity.firstName = firstName;
		entity.lastName = lastName;
		entity.displayName = displayName;
		entity.status = status;
		entity.validFrom = validFrom;
		entity.expiresAt = expiresAt;
		entity.failedLoginAttempts = 0;
		entity.passwordChangeRequired = true;
		entity.temporaryPasswordExpiresAt = temporaryPasswordExpiresAt;
		entity.createdBy = actorId;
		entity.updatedBy = actorId;
		entity.createdAt = now;
		entity.updatedAt = now;
		entity.version = 0L;
		return entity;
	}

	public StudentEffectiveStatus effectiveStatusAt(Instant now) {
        if (status == StudentStatus.DELETED) return StudentEffectiveStatus.DELETED;
        if (status == StudentStatus.INACTIVE) return StudentEffectiveStatus.INACTIVE;
        if (status == StudentStatus.EXPIRED || (expiresAt != null && !now.isBefore(expiresAt))) {
            return StudentEffectiveStatus.EXPIRED;
        }
        return StudentEffectiveStatus.ACTIVE;
    }

    public boolean canAuthenticateAt(Instant now) {
        return status == StudentStatus.ACTIVE
                && (validFrom == null || !now.isBefore(validFrom))
                && (expiresAt == null || now.isBefore(expiresAt))
                && (lockedUntil == null || !lockedUntil.isAfter(now));
    }

    public boolean isTemporaryPasswordExpiredAt(Instant now) {
        return passwordChangeRequired && temporaryPasswordExpiresAt != null && !temporaryPasswordExpiresAt.isAfter(now);
    }

    public void registerFailedLogin(int maxAttempts, Instant now, java.time.Duration lockDuration) {
        failedLoginAttempts++;
        if (failedLoginAttempts >= maxAttempts) {
            lockedUntil = now.plus(lockDuration);
            failedLoginAttempts = 0;
        }
        touch(updatedBy, now);
    }

    public void registerSuccessfulLogin(Instant now) {
        failedLoginAttempts = 0;
        lockedUntil = null;
        lastLoginAt = now;
        updatedAt = now;
    }

    public void updateProfile(String email, String normalizedEmail, String firstName, String lastName,
            String displayName, Instant validFrom, Instant expiresAt, Long actorId, Instant now) {
        this.email = email;
        this.normalizedEmail = normalizedEmail;
        this.firstName = firstName;
        this.lastName = lastName;
        this.displayName = displayName;
        this.validFrom = validFrom;
        this.expiresAt = expiresAt;
        if (this.status == StudentStatus.ACTIVE && expiresAt != null && !expiresAt.isAfter(now)) {
            this.status = StudentStatus.EXPIRED;
        }
        touch(actorId, now);
    }

    public void activate(Long actorId, Instant now) {
        this.status = StudentStatus.ACTIVE;
        this.archivedAt = null;
        this.deletedAt = null;
        this.deletedBy = null;
        this.deletionReason = null;
        touch(actorId, now);
    }

    public void deactivate(Long actorId, Instant now) {
        this.status = StudentStatus.INACTIVE;
        touch(actorId, now);
    }

    public void expire(Long actorId, Instant now) {
        this.status = StudentStatus.EXPIRED;
        touch(actorId, now);
    }

    public void renew(Instant newExpiresAt, Long actorId, Instant now) {
        this.expiresAt = newExpiresAt;
        this.status = StudentStatus.ACTIVE;
        touch(actorId, now);
    }

    /** Estado transitorio que bloquea operaciones dentro de la transacción de purga. */
    public void markDeleting(Long actorId, Instant now) {
        this.status = StudentStatus.DELETED;
        this.deletedAt = now;
        this.deletedBy = actorId;
        this.deletionReason = "PERMANENT_DELETION_IN_PROGRESS";
        touch(actorId, now);
    }

	public void resetPassword(String passwordHash, Instant temporaryPasswordExpiresAt, Long actorId, Instant now) {
		this.passwordHash = passwordHash;
		this.passwordChangeRequired = true;
		this.temporaryPasswordExpiresAt = temporaryPasswordExpiresAt;
		this.passwordChangedAt = now;
		this.failedLoginAttempts = 0;
		this.lockedUntil = null;
		touch(actorId, now);
	}

	public void changePassword(String passwordHash, Instant now) {
		this.passwordHash = passwordHash;
		this.passwordChangeRequired = false;
		this.temporaryPasswordExpiresAt = null;
		this.passwordChangedAt = now;
		this.failedLoginAttempts = 0;
		this.lockedUntil = null;
		this.updatedAt = now;
	}

	private void touch(Long actorId, Instant now) {
		this.updatedBy = actorId;
		this.updatedAt = now;
	}

	public Long getId() {
		return id;
	}

	public String getPublicId() {
		return publicId;
	}

	public Long getOrganizationId() {
		return organizationId;
	}

	public String getStudentCode() {
		return studentCode;
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

	public StudentStatus getStatus() {
		return status;
	}

	public Instant getValidFrom() {
		return validFrom;
	}

	public Instant getExpiresAt() {
		return expiresAt;
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

	public Instant getPasswordChangedAt() {
		return passwordChangedAt;
	}

	public boolean isPasswordChangeRequired() {
		return passwordChangeRequired;
	}

	public Instant getTemporaryPasswordExpiresAt() {
		return temporaryPasswordExpiresAt;
	}

	public Instant getArchivedAt() {
		return archivedAt;
	}

	public Instant getDeletedAt() {
		return deletedAt;
	}

	public String getDeletionReason() {
		return deletionReason;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public Long getUpdatedBy() {
		return updatedBy;
	}

	public Long getVersion() {
		return version;
	}
}
