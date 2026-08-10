package com.nexoskill.evaluation.organizations.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "ORGANIZATION_BRANDING")
public class OrganizationBrandingJpaEntity {
    @Id
    @Column(name = "ORGANIZATION_ID")
    private Long organizationId;

    @Lob
    @Column(name = "LOGO_CONTENT")
    private byte[] logoContent;

    @Column(name = "LOGO_CONTENT_TYPE", length = 100)
    private String logoContentType;

    @Column(name = "LOGO_FILE_NAME", length = 255)
    private String logoFileName;

    @Column(name = "LOGO_UPDATED_AT")
    private Instant logoUpdatedAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "VERSION_NO", nullable = false)
    private Long version;

    protected OrganizationBrandingJpaEntity() {}

    public static OrganizationBrandingJpaEntity create(Long organizationId, byte[] content, String contentType,
            String fileName, Instant now) {
        OrganizationBrandingJpaEntity entity = new OrganizationBrandingJpaEntity();
        entity.organizationId = organizationId;
        entity.replaceLogo(content, contentType, fileName, now);
        return entity;
    }

    public void replaceLogo(byte[] content, String contentType, String fileName, Instant now) {
        this.logoContent = content == null ? null : content.clone();
        this.logoContentType = contentType;
        this.logoFileName = fileName;
        this.logoUpdatedAt = now;
        this.updatedAt = now;
    }

    public Long getOrganizationId() { return organizationId; }
    public byte[] getLogoContent() { return logoContent == null ? null : logoContent.clone(); }
    public String getLogoContentType() { return logoContentType; }
    public String getLogoFileName() { return logoFileName; }
    public Instant getLogoUpdatedAt() { return logoUpdatedAt; }
    public Long getVersion() { return version; }
}
