package com.nexoskill.evaluation.globalcontent.infrastructure.persistence;

import com.nexoskill.evaluation.globalcontent.domain.model.*;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "ORGANIZATION_GLOBAL_CONTENT_GRANT")
public class OrganizationGlobalContentGrantJpaEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "GRANT_ID")
	private Long id;

	@Column(name = "PUBLIC_ID", nullable = false, unique = true, length = 36)
	private String publicId;

	@Column(name = "ORGANIZATION_ID", nullable = false)
	private Long organizationId;

	@Enumerated(EnumType.STRING)
	@Column(name = "CONTENT_TYPE", nullable = false, length = 30)
	private GlobalContentType contentType;

	@Column(name = "GLOBAL_CONTENT_ID", nullable = false)
	private Long globalContentId;

	@Column(name = "GLOBAL_CONTENT_PUBLIC_ID", nullable = false, length = 36)
	private String globalContentPublicId;

	@Column(name = "GLOBAL_VERSION", nullable = false)
	private long globalVersion;

	@Enumerated(EnumType.STRING)
	@Column(name = "STATUS", nullable = false, length = 20)
	private GrantStatus status;

	@Enumerated(EnumType.STRING)
	@Column(name = "DISTRIBUTION_MODE", nullable = false, length = 30)
	private DistributionMode distributionMode;

	@Enumerated(EnumType.STRING)
	@Column(name = "ACCESS_MODE", nullable = false, length = 30)
	private AccessMode accessMode;

	@Column(name = "CLONING_ALLOWED", nullable = false)
	private Integer cloningAllowed;

	@Column(name = "ORGANIZATION_EDITABLE", nullable = false)
	private Integer organizationEditable;

	@Enumerated(EnumType.STRING)
	@Column(name = "UPDATE_POLICY", nullable = false, length = 30)
	private UpdatePolicy updatePolicy;

	@Column(name = "AVAILABLE_FROM")
	private Instant availableFrom;

	@Column(name = "EXPIRES_AT")
	private Instant expiresAt;

	@Column(name = "ENABLED_BY", nullable = false)
	private Long enabledBy;

	@Column(name = "ENABLED_AT", nullable = false)
	private Instant enabledAt;

	@Column(name = "DISABLED_BY")
	private Long disabledBy;

	@Column(name = "DISABLED_AT")
	private Instant disabledAt;

	@Version
	@Column(name = "VERSION_NO", nullable = false)
	private long entityVersion;

	protected OrganizationGlobalContentGrantJpaEntity() {
	}

	public static OrganizationGlobalContentGrantJpaEntity create(String publicId, Long organizationId,
			GlobalContentType contentType, Long globalContentId, String globalContentPublicId, long globalVersion,
			DistributionMode distributionMode, AccessMode accessMode, boolean cloningAllowed,
			boolean organizationEditable, UpdatePolicy updatePolicy, Instant availableFrom, Instant expiresAt,
			Long actor, Instant now) {
		OrganizationGlobalContentGrantJpaEntity entity = new OrganizationGlobalContentGrantJpaEntity();
		entity.publicId = publicId;
		entity.organizationId = organizationId;
		entity.contentType = contentType;
		entity.globalContentId = globalContentId;
		entity.globalContentPublicId = globalContentPublicId;
		entity.globalVersion = globalVersion;
		entity.status = GrantStatus.ACTIVE;
		entity.distributionMode = distributionMode;
		entity.accessMode = accessMode;
		entity.cloningAllowed = cloningAllowed ? 1 : 0;
		entity.organizationEditable = organizationEditable ? 1 : 0;
		entity.updatePolicy = updatePolicy;
		entity.availableFrom = availableFrom;
		entity.expiresAt = expiresAt;
		entity.enabledBy = actor;
		entity.enabledAt = now;
		return entity;
	}

	public void reactivate(Long actor, Instant now) {
		status = GrantStatus.ACTIVE;
		enabledBy = actor;
		enabledAt = now;
		disabledBy = null;
		disabledAt = null;
	}

	public void reconfigure(AccessMode accessMode, boolean cloningAllowed, boolean organizationEditable,
			UpdatePolicy updatePolicy, Instant availableFrom, Instant expiresAt, Long actor, Instant now) {
		this.accessMode = accessMode;
		this.cloningAllowed = cloningAllowed ? 1 : 0;
		this.organizationEditable = organizationEditable ? 1 : 0;
		this.updatePolicy = updatePolicy;
		this.availableFrom = availableFrom;
		this.expiresAt = expiresAt;
		reactivate(actor, now);
	}

	public void disable(Long actor, Instant now) {
		status = GrantStatus.INACTIVE;
		disabledBy = actor;
		disabledAt = now;
	}

	public boolean isOperational(Instant now) {
		return status == GrantStatus.ACTIVE && (availableFrom == null || !availableFrom.isAfter(now))
				&& (expiresAt == null || expiresAt.isAfter(now));
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

	public GlobalContentType getContentType() {
		return contentType;
	}

	public Long getGlobalContentId() {
		return globalContentId;
	}

	public String getGlobalContentPublicId() {
		return globalContentPublicId;
	}

	public long getGlobalVersion() {
		return globalVersion;
	}

	public GrantStatus getStatus() {
		return status;
	}

	public DistributionMode getDistributionMode() {
		return distributionMode;
	}

	public AccessMode getAccessMode() {
		return accessMode;
	}

	public boolean isCloningAllowed() {
		return Integer.valueOf(1).equals(cloningAllowed);
	}

	public boolean isOrganizationEditable() {
		return Integer.valueOf(1).equals(organizationEditable);
	}

	public UpdatePolicy getUpdatePolicy() {
		return updatePolicy;
	}

	public Instant getAvailableFrom() {
		return availableFrom;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public Instant getEnabledAt() {
		return enabledAt;
	}
}
