package com.nexoskill.evaluation.students.infrastructure.persistence;

import com.nexoskill.evaluation.students.domain.StudentEffectiveStatus;
import com.nexoskill.evaluation.students.domain.StudentStatus;
import com.nexoskill.evaluation.students.domain.StudentRecordModule;
import com.nexoskill.evaluation.students.domain.TalentProfileCode;
import com.nexoskill.evaluation.students.domain.TalentType;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "RECORD_MODULE", nullable = false, length = 30)
    private StudentRecordModule recordModule;

    @Enumerated(EnumType.STRING)
    @Column(name = "TALENT_TYPE", length = 30)
    private TalentType talentType;

    @Column(name = "ORGANIZATION_HIRED_ON")
    private LocalDate organizationHiredOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "TALENT_PROFILE_CODE", length = 10)
    private TalentProfileCode talentProfileCode;

    @Column(name = "TALENT_TECHNOLOGY_ID")
    private Long talentTechnologyId;

    @Column(name = "TALENT_MOVED_AT")
    private Instant talentMovedAt;

    @Column(name = "TALENT_MOVED_BY")
    private Long talentMovedBy;

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

    /** Columnas legadas conservadas para compatibilidad con instalaciones anteriores. */
    @Column(name = "VALID_FROM", nullable = false)
    private Instant legacyValidFrom;

    @Column(name = "EXPIRES_AT")
    private Instant legacyExpiresAt;

    /** Fechas de calendario que controlan el acceso desde V042. */
    @Column(name = "ACCESS_VALID_FROM", nullable = false)
    private LocalDate validFrom;

    @Column(name = "ACCESS_EXPIRES_ON")
    private LocalDate expiresAt;

    @Column(name = "ADMISSION_DATE")
    private LocalDate admissionDate;

    @Column(name = "CORPORATE_USER", length = 100)
    private String corporateUser;

    @Column(name = "NORMALIZED_CORPORATE_USER", length = 100)
    private String normalizedCorporateUser;

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
            StudentStatus status, LocalDate validFrom, LocalDate expiresAt, Instant temporaryPasswordExpiresAt,
            Long actorId, Instant now) {
        return create(publicId, organizationId, studentCode, email, normalizedEmail, passwordHash, firstName,
                lastName, displayName, status, validFrom, expiresAt, validFrom, null, null,
                temporaryPasswordExpiresAt, actorId, now);
    }

    public static StudentJpaEntity create(String publicId, Long organizationId, String studentCode, String email,
            String normalizedEmail, String passwordHash, String firstName, String lastName, String displayName,
            StudentStatus status, LocalDate validFrom, LocalDate expiresAt, LocalDate admissionDate,
            String corporateUser, String normalizedCorporateUser, Instant temporaryPasswordExpiresAt,
            Long actorId, Instant now) {
        StudentJpaEntity entity = new StudentJpaEntity();
        entity.publicId = publicId;
        entity.organizationId = organizationId;
        entity.recordModule = StudentRecordModule.COLLABORATOR;
        entity.studentCode = studentCode;
        entity.email = email;
        entity.normalizedEmail = normalizedEmail;
        entity.passwordHash = passwordHash;
        entity.firstName = firstName;
        entity.lastName = lastName;
        entity.displayName = displayName;
        entity.status = admissionDate == null ? StudentStatus.INACTIVE : status;
        entity.setAccessDates(validFrom, expiresAt);
        entity.admissionDate = admissionDate;
        entity.corporateUser = corporateUser;
        entity.normalizedCorporateUser = normalizedCorporateUser;
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

    public StudentEffectiveStatus effectiveStatusOn(LocalDate today) {
        if (status == StudentStatus.DELETED) return StudentEffectiveStatus.DELETED;
        if (recordModule == StudentRecordModule.TALENT_BANK) return StudentEffectiveStatus.INACTIVE;
        if (admissionDate == null || status == StudentStatus.INACTIVE) return StudentEffectiveStatus.INACTIVE;
        if (status == StudentStatus.EXPIRED || (expiresAt != null && today.isAfter(expiresAt))) {
            return StudentEffectiveStatus.EXPIRED;
        }
        return StudentEffectiveStatus.ACTIVE;
    }

    public StudentEffectiveStatus effectiveStatusAt(Instant now) {
        return effectiveStatusOn(LocalDate.ofInstant(now, ZoneOffset.UTC));
    }

    public boolean canAuthenticateOn(LocalDate today, Instant now) {
        return recordModule == StudentRecordModule.COLLABORATOR
                && status == StudentStatus.ACTIVE
                && admissionDate != null
                && validFrom != null && !today.isBefore(validFrom)
                && expiresAt != null && !today.isAfter(expiresAt)
                && (lockedUntil == null || !lockedUntil.isAfter(now));
    }

    public boolean canAuthenticateAt(Instant now) {
        return canAuthenticateOn(LocalDate.ofInstant(now, ZoneOffset.UTC), now);
    }

    public Instant accessExpirationInstant(ZoneId zoneId) {
        return expiresAt == null ? null : expiresAt.plusDays(1).atStartOfDay(zoneId).toInstant();
    }

    public Instant accessExpirationInstant() {
        return accessExpirationInstant(ZoneOffset.UTC);
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

    public void assignStudentCode(String studentCode, Long actorId, Instant now) {
        this.studentCode = studentCode;
        touch(actorId, now);
    }

    public void updateProfile(String studentCode, String email, String normalizedEmail, String firstName, String lastName,
            String displayName, LocalDate validFrom, LocalDate expiresAt, LocalDate admissionDate,
            String corporateUser, String normalizedCorporateUser, Long actorId, Instant now) {
        this.studentCode = studentCode;
        this.email = email;
        this.normalizedEmail = normalizedEmail;
        this.firstName = firstName;
        this.lastName = lastName;
        this.displayName = displayName;
        setAccessDates(validFrom, expiresAt);
        this.admissionDate = admissionDate;
        this.corporateUser = corporateUser;
        this.normalizedCorporateUser = normalizedCorporateUser;
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        if (recordModule == StudentRecordModule.TALENT_BANK || admissionDate == null) {
            this.status = StudentStatus.INACTIVE;
        } else if (expiresAt != null && today.isAfter(expiresAt)) {
            this.status = StudentStatus.EXPIRED;
        } else if (this.status != StudentStatus.DELETED) {
            this.status = StudentStatus.ACTIVE;
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
        this.admissionDate = null;
        touch(actorId, now);
    }

    public void moveToTalentBank(TalentType type, Long actorId, Instant now) {
        if (type == null) throw new IllegalArgumentException("El tipo de talento es obligatorio.");
        this.recordModule = StudentRecordModule.TALENT_BANK;
        this.talentType = type;
        this.status = StudentStatus.INACTIVE;
        this.talentMovedAt = now;
        this.talentMovedBy = actorId;
        touch(actorId, now);
    }

    public void configureTalent(TalentType type, LocalDate organizationHiredOn, TalentProfileCode profileCode,
            Long technologyId, Long actorId, Instant now) {
        moveToTalentBank(type, actorId, now);
        updateTalentMetadata(organizationHiredOn, profileCode, technologyId, actorId, now);
    }

    public void updateTalentMetadata(LocalDate organizationHiredOn, TalentProfileCode profileCode,
            Long technologyId, Long actorId, Instant now) {
        this.organizationHiredOn = organizationHiredOn;
        this.talentProfileCode = profileCode;
        this.talentTechnologyId = technologyId;
        touch(actorId, now);
    }

    public void convertToCollaborator(LocalDate admissionDate, Long actorId, Instant now) {
        this.recordModule = StudentRecordModule.COLLABORATOR;
        this.talentType = null;
        this.admissionDate = admissionDate;
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        if (admissionDate == null) {
            this.status = StudentStatus.INACTIVE;
        } else if (expiresAt != null && today.isAfter(expiresAt)) {
            this.status = StudentStatus.EXPIRED;
        } else {
            this.status = StudentStatus.ACTIVE;
        }
        this.talentMovedAt = now;
        this.talentMovedBy = actorId;
        touch(actorId, now);
    }

    public void expire(Long actorId, Instant now) {
        this.status = StudentStatus.EXPIRED;
        touch(actorId, now);
    }

    public void softDelete(Long actorId, Instant now, String reason) {
        this.status = StudentStatus.DELETED;
        this.deletedAt = now;
        this.deletedBy = actorId;
        this.deletionReason = reason == null || reason.isBlank()
                ? "ELIMINACIÓN LÓGICA"
                : reason.trim();
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

    private void setAccessDates(LocalDate validFrom, LocalDate expiresAt) {
        this.validFrom = validFrom;
        this.expiresAt = expiresAt;
        this.legacyValidFrom = validFrom == null ? null : validFrom.atStartOfDay(ZoneOffset.UTC).toInstant();
        this.legacyExpiresAt = expiresAt == null ? null
                : expiresAt.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private void touch(Long actorId, Instant now) {
        this.updatedBy = actorId;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public String getPublicId() { return publicId; }
    public Long getOrganizationId() { return organizationId; }
    public StudentRecordModule getRecordModule() { return recordModule; }
    public TalentType getTalentType() { return talentType; }
    public LocalDate getOrganizationHiredOn() { return organizationHiredOn; }
    public TalentProfileCode getTalentProfileCode() { return talentProfileCode; }
    public Long getTalentTechnologyId() { return talentTechnologyId; }
    public Instant getTalentMovedAt() { return talentMovedAt; }
    public Long getTalentMovedBy() { return talentMovedBy; }
    public String getStudentCode() { return studentCode; }
    public String getEmail() { return email; }
    public String getNormalizedEmail() { return normalizedEmail; }
    public String getPasswordHash() { return passwordHash; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getDisplayName() { return displayName; }
    public StudentStatus getStatus() { return status; }
    public LocalDate getValidFrom() { return validFrom; }
    public LocalDate getExpiresAt() { return expiresAt; }
    public LocalDate getAdmissionDate() { return admissionDate; }
    public String getCorporateUser() { return corporateUser; }
    public String getNormalizedCorporateUser() { return normalizedCorporateUser; }
    public int getFailedLoginAttempts() { return failedLoginAttempts; }
    public Instant getLockedUntil() { return lockedUntil; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public Instant getPasswordChangedAt() { return passwordChangedAt; }
    public boolean isPasswordChangeRequired() { return passwordChangeRequired; }
    public Instant getTemporaryPasswordExpiresAt() { return temporaryPasswordExpiresAt; }
    public Instant getArchivedAt() { return archivedAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public Long getDeletedBy() { return deletedBy; }
    public String getDeletionReason() { return deletionReason; }
    public Long getCreatedBy() { return createdBy; }
    public Long getUpdatedBy() { return updatedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
