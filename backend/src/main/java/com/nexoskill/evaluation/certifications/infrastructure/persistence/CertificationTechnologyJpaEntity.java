package com.nexoskill.evaluation.certifications.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "CERTIFICATION_TECHNOLOGY_CATALOG")
public class CertificationTechnologyJpaEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "CERTIFICATION_TECHNOLOGY_ID") private Long id;
    @Column(name = "PUBLIC_ID", nullable = false, length = 36, unique = true) private String publicId;
    @Column(name = "TECHNOLOGY_CODE", nullable = false, length = 120, unique = true) private String code;
    @Column(name = "TECHNOLOGY_NAME", nullable = false, length = 200) private String name;
    @Column(name = "STATUS", nullable = false, length = 20) private String status;
    @Column(name = "SORT_ORDER", nullable = false) private int sortOrder;
    @Column(name = "MASTER_TECHNOLOGY_ID") private Long masterTechnologyId;
    @Column(name = "CREATED_AT", nullable = false) private Instant createdAt;
    @Column(name = "UPDATED_AT", nullable = false) private Instant updatedAt;
    @Version @Column(name = "VERSION_NO", nullable = false) private Long version;
    protected CertificationTechnologyJpaEntity() {}
    public static CertificationTechnologyJpaEntity createFromMaster(String publicId, String code, String name,
            String status, int sortOrder, Long masterTechnologyId, Instant now) {
        CertificationTechnologyJpaEntity entity = new CertificationTechnologyJpaEntity();
        entity.publicId = publicId;
        entity.code = code;
        entity.name = name;
        entity.status = status;
        entity.sortOrder = sortOrder;
        entity.masterTechnologyId = masterTechnologyId;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }
    public void synchronizeWithMaster(String name, String status, int sortOrder,
            Long masterTechnologyId, Instant now) {
        this.name = name;
        this.status = status;
        this.sortOrder = sortOrder;
        this.masterTechnologyId = masterTechnologyId;
        this.updatedAt = now;
    }
    public Long getId() { return id; }
    public String getPublicId() { return publicId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getStatus() { return status; }
    public int getSortOrder() { return sortOrder; }
    public Long getMasterTechnologyId() { return masterTechnologyId; }
}
