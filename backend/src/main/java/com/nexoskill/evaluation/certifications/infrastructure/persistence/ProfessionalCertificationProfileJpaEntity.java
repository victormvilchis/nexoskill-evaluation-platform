package com.nexoskill.evaluation.certifications.infrastructure.persistence;

import com.nexoskill.evaluation.certifications.domain.TechnologicalProfile;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "CERTIFICATION_PROFILE_CATALOG")
public class ProfessionalCertificationProfileJpaEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "CERTIFICATION_PROFILE_ID") private Long id;
    @Column(name = "PUBLIC_ID", nullable = false, length = 36, unique = true) private String publicId;
    @Column(name = "PROFILE_CODE", nullable = false, length = 120, unique = true) private String code;
    @Column(name = "PROFILE_NAME", nullable = false, length = 200) private String name;
    @Column(name = "STATUS", nullable = false, length = 20) private String status;
    @Enumerated(EnumType.STRING)
    @Column(name = "SUGGESTED_TECH_PROFILE", length = 40) private TechnologicalProfile suggestedTechnologicalProfile;
    @Column(name = "SORT_ORDER", nullable = false) private int sortOrder;
    @Column(name = "CREATED_AT", nullable = false) private Instant createdAt;
    @Column(name = "UPDATED_AT", nullable = false) private Instant updatedAt;
    @Version @Column(name = "VERSION_NO", nullable = false) private Long version;
    protected ProfessionalCertificationProfileJpaEntity() {}
    public Long getId() { return id; }
    public String getPublicId() { return publicId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getStatus() { return status; }
    public TechnologicalProfile getSuggestedTechnologicalProfile() { return suggestedTechnologicalProfile; }
    public int getSortOrder() { return sortOrder; }
}
