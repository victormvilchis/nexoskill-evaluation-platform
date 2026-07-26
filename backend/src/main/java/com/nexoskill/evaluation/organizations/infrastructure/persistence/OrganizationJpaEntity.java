package com.nexoskill.evaluation.organizations.infrastructure.persistence;

import com.nexoskill.evaluation.organizations.domain.model.ContentMode;
import com.nexoskill.evaluation.organizations.domain.model.OrganizationStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "ORGANIZATION")
public class OrganizationJpaEntity {
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
    @Column(name = "STATUS", nullable = false, length = 20)
    private OrganizationStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "CONTENT_MODE", nullable = false, length = 30)
    private ContentMode contentMode;

    @Column(name = "VALID_FROM")
    private LocalDate validFrom;

    @Column(name = "EXPIRES_ON")
    private LocalDate expiresOn;

    @Column(name = "CREATED_BY")
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

    protected OrganizationJpaEntity() {}

    public static OrganizationJpaEntity create(String publicId, String code, String name, ContentMode mode,
                                                LocalDate validFrom, LocalDate expiresOn, Long actorId, Instant now) {
        OrganizationJpaEntity entity = new OrganizationJpaEntity();
        entity.publicId = publicId;
        entity.code = code;
        entity.name = name;
        entity.status = OrganizationStatus.ACTIVE;
        entity.contentMode = mode;
        entity.validFrom = validFrom;
        entity.expiresOn = expiresOn;
        entity.createdBy = actorId;
        entity.updatedBy = actorId;
        entity.createdAt = now;
        entity.updatedAt = now;
        entity.version = 0L;
        return entity;
    }

    public void update(String name, ContentMode mode, LocalDate validFrom, LocalDate expiresOn,
                       Long actorId, Instant now) {
        this.name = name;
        this.contentMode = mode;
        this.validFrom = validFrom;
        this.expiresOn = expiresOn;
        this.updatedBy = actorId;
        this.updatedAt = now;
    }

    public void changeStatus(OrganizationStatus status, Long actorId, Instant now) {
        this.status = status;
        this.updatedBy = actorId;
        this.updatedAt = now;
    }

    public boolean isOperational(LocalDate today) {
        return status == OrganizationStatus.ACTIVE
                && (validFrom == null || !today.isBefore(validFrom))
                && (expiresOn == null || !today.isAfter(expiresOn));
    }

    public Long getId() { return id; }
    public String getPublicId() { return publicId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public OrganizationStatus getStatus() { return status; }
    public ContentMode getContentMode() { return contentMode; }
    public LocalDate getValidFrom() { return validFrom; }
    public LocalDate getExpiresOn() { return expiresOn; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
