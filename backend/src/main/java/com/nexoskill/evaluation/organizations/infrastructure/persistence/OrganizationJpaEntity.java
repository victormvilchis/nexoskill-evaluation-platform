package com.nexoskill.evaluation.organizations.infrastructure.persistence;

import com.nexoskill.evaluation.organizations.domain.model.ContentMode;
import com.nexoskill.evaluation.organizations.domain.model.OrganizationStatus;
import com.nexoskill.evaluation.organizations.domain.model.OrganizationType;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "ORGANIZATION")
public class OrganizationJpaEntity {
	public static final String GLOBAL_CODE = "GLOBAL";
	public static final String GLOBAL_PUBLIC_ID = "00000000-0000-0000-0000-000000000001";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "ORGANIZATION_ID")
	private Long id;

	@Column(name = "PUBLIC_ID", nullable = false, length = 36, unique = true)
	private String publicId;

	@Column(name = "ORGANIZATION_CODE", nullable = false, length = 80, unique = true)
	private String code;

	@Column(name = "ORGANIZATION_NAME", nullable = false, length = 200)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(name = "ORGANIZATION_TYPE", nullable = false, length = 20)
	private OrganizationType organizationType;

	@Enumerated(EnumType.STRING)
	@Column(name = "STATUS", nullable = false, length = 20)
	private OrganizationStatus status;

	@Enumerated(EnumType.STRING)
	@Column(name = "CONTENT_MODE", nullable = false, length = 30)
	private ContentMode contentMode;

	@Column(name = "VALID_FROM", nullable = false)
	private LocalDate validFrom;

	@Column(name = "EXPIRES_ON")
	private LocalDate expiresOn;

	@Column(name = "APPLIES_CERTIFICATIONS", nullable = false)
	private boolean appliesCertifications;

	@Column(name = "MANUAL_STUDENT_CODE", nullable = false)
	private boolean manualStudentCode;

	@Column(name = "CREATED_BY")
	private Long createdBy;

	@Column(name = "UPDATED_BY")
	private Long updatedBy;

	@Column(name = "CREATED_AT", nullable = false)
	private Instant createdAt;

	@Column(name = "UPDATED_AT", nullable = false)
	private Instant updatedAt;

	@Column(name = "STATUS_CHANGED_AT")
	private Instant statusChangedAt;

	@Column(name = "STATUS_CHANGED_BY")
	private Long statusChangedBy;

	@Column(name = "STATUS_REASON", length = 500)
	private String statusReason;

	@Column(name = "DELETED_AT")
	private Instant deletedAt;

	@Column(name = "DELETED_BY")
	private Long deletedBy;

	@Version
	@Column(name = "VERSION_NO", nullable = false)
	private Long version;

	protected OrganizationJpaEntity() {
	}

	/** Compatibilidad de construcción para pruebas y adaptadores previos. */
	public static OrganizationJpaEntity create(String publicId, String code, String name, ContentMode mode,
			LocalDate validFrom, LocalDate expiresOn, Long actorId, Instant now) {
		return createCustomer(publicId, code, name, mode, validFrom, expiresOn, actorId, now);
	}

	public static OrganizationJpaEntity createCustomer(String publicId, String code, String name, ContentMode mode,
			LocalDate validFrom, LocalDate expiresOn, Long actorId, Instant now) {
		OrganizationJpaEntity entity = new OrganizationJpaEntity();
		entity.publicId = publicId;
		entity.code = code;
		entity.name = name;
		entity.organizationType = OrganizationType.CUSTOMER;
		entity.status = OrganizationStatus.ACTIVE;
		entity.contentMode = mode;
		entity.validFrom = validFrom;
		entity.expiresOn = expiresOn;
		entity.appliesCertifications = false;
		entity.manualStudentCode = false;
		entity.createdBy = actorId;
		entity.updatedBy = actorId;
		entity.createdAt = now;
		entity.updatedAt = now;
		entity.statusChangedAt = now;
		entity.statusChangedBy = actorId;
		// La versión debe permanecer nula en entidades nuevas para que Spring Data use
		// persist, no merge.
		return entity;
	}

	/** Compatibilidad temporal; validFrom es inmutable después del alta. */
	public void update(String name, ContentMode mode, LocalDate ignoredValidFrom, LocalDate expiresOn, Long actorId,
			Instant now) {
		updateCustomer(name, mode, expiresOn, actorId, now);
	}

	public void updateCustomer(String name, ContentMode mode, LocalDate expiresOn, Long actorId, Instant now) {
		ensureCustomerMutable();
		this.name = name;
		this.contentMode = mode;
		this.expiresOn = expiresOn;
		this.updatedBy = actorId;
		this.updatedAt = now;
	}

	public void transitionTo(OrganizationStatus nextStatus, String reason, Long actorId, Instant now) {
		ensureCustomerMutable();
		if (nextStatus == null) {
			throw new IllegalArgumentException("El estado de la organización es obligatorio.");
		}
		this.status = nextStatus;
		this.statusChangedAt = now;
		this.statusChangedBy = actorId;
		this.statusReason = normalizeReason(reason);
		this.updatedBy = actorId;
		this.updatedAt = now;
		if (nextStatus == OrganizationStatus.DELETED) {
			this.deletedAt = now;
			this.deletedBy = actorId;
		} else {
			this.deletedAt = null;
			this.deletedBy = null;
		}
	}

	/**
	 * Compatibilidad con el endpoint anterior. Las reglas de transición viven en el
	 * servicio.
	 */
	public void changeStatus(OrganizationStatus status, Long actorId, Instant now) {
		transitionTo(status, null, actorId, now);
	}

	private static String normalizeReason(String value) {
		if (value == null || value.isBlank())
			return null;
		String normalized = value.trim();
		return normalized.length() <= 500 ? normalized : normalized.substring(0, 500);
	}

	public void configureCertifications(boolean appliesCertifications, Long actorId, Instant now) {
		ensureCustomerMutable();
		this.appliesCertifications = appliesCertifications;
		this.updatedBy = actorId;
		this.updatedAt = now;
	}

	public void configureStudentCode(boolean manualStudentCode, Long actorId, Instant now) {
		ensureCustomerMutable();
		this.manualStudentCode = manualStudentCode;
		this.updatedBy = actorId;
		this.updatedAt = now;
	}

	private void ensureCustomerMutable() {
		if (organizationType == OrganizationType.GLOBAL) {
			throw new IllegalStateException("La organización global no puede modificarse desde el flujo comercial.");
		}
	}

	public boolean isOperational(LocalDate today) {
		if (organizationType == OrganizationType.GLOBAL)
			return true;
		return status == OrganizationStatus.ACTIVE && !today.isBefore(validFrom)
				&& (expiresOn == null || !today.isAfter(expiresOn));
	}

	public boolean isGlobal() {
		return organizationType == OrganizationType.GLOBAL;
	}

	public Long getId() {
		return id;
	}

	public String getPublicId() {
		return publicId;
	}

	public String getCode() {
		return code;
	}

	public String getName() {
		return name;
	}

	public OrganizationType getOrganizationType() {
		return organizationType;
	}

	public OrganizationStatus getStatus() {
		return status;
	}

	public ContentMode getContentMode() {
		return contentMode;
	}

	public LocalDate getValidFrom() {
		return validFrom;
	}

	public LocalDate getExpiresOn() {
		return expiresOn;
	}

	public boolean isAppliesCertifications() {
		return appliesCertifications;
	}

	public boolean isManualStudentCode() {
		return manualStudentCode;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public Instant getStatusChangedAt() {
		return statusChangedAt;
	}

	public Long getStatusChangedBy() {
		return statusChangedBy;
	}

	public String getStatusReason() {
		return statusReason;
	}

	public Instant getDeletedAt() {
		return deletedAt;
	}

	public Long getDeletedBy() {
		return deletedBy;
	}

	public Long getVersion() {
		return version;
	}
}
