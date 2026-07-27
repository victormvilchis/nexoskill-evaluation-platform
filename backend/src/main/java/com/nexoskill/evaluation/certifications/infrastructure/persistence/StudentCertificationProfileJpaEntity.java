package com.nexoskill.evaluation.certifications.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "STUDENT_CERTIFICATION_PROFILE")
public class StudentCertificationProfileJpaEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "STUDENT_CERTIFICATION_PROFILE_ID") private Long id;
    @Column(name = "PUBLIC_ID", nullable = false, length = 36, unique = true) private String publicId;
    @Column(name = "STUDENT_ID", nullable = false, unique = true) private Long studentId;
    @Column(name = "ORGANIZATION_ID", nullable = false) private Long organizationId;
    @Column(name = "PROFESSIONAL_PROFILE_ID", nullable = false) private Long professionalProfileId;
    @Column(name = "CERTIFICATION_TECHNOLOGY_ID", nullable = false) private Long certificationTechnologyId;
    @Column(name = "ENROLLMENT_DATE", nullable = false) private LocalDate enrollmentDate;
    @Column(name = "TECHNOLOGICAL_PROFILE", nullable = false, length = 40) private String technologicalProfile;
    @Column(name = "STATUS", nullable = false, length = 20) private String status;
    @Column(name = "CREATED_BY", nullable = false) private Long createdBy;
    @Column(name = "UPDATED_BY", nullable = false) private Long updatedBy;
    @Column(name = "CREATED_AT", nullable = false) private Instant createdAt;
    @Column(name = "UPDATED_AT", nullable = false) private Instant updatedAt;
    @Version @Column(name = "VERSION_NO", nullable = false) private Long version;
    protected StudentCertificationProfileJpaEntity() {}
    public static StudentCertificationProfileJpaEntity create(Long studentId, Long organizationId,
            Long professionalProfileId, Long technologyId, LocalDate enrollmentDate,
            String technologicalProfile, Long actorId, Instant now) {
        StudentCertificationProfileJpaEntity entity = new StudentCertificationProfileJpaEntity();
        entity.publicId = java.util.UUID.randomUUID().toString();
        entity.studentId = studentId;
        entity.organizationId = organizationId;
        entity.professionalProfileId = professionalProfileId;
        entity.certificationTechnologyId = technologyId;
        entity.enrollmentDate = enrollmentDate;
        entity.technologicalProfile = technologicalProfile;
        entity.status = "ACTIVE";
        entity.createdBy = actorId;
        entity.updatedBy = actorId;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }
    public void update(Long professionalProfileId, Long technologyId, LocalDate enrollmentDate,
            String technologicalProfile, Long actorId, Instant now) {
        this.professionalProfileId = professionalProfileId;
        this.certificationTechnologyId = technologyId;
        this.enrollmentDate = enrollmentDate;
        this.technologicalProfile = technologicalProfile;
        this.updatedBy = actorId;
        this.updatedAt = now;
    }
    public Long getId() { return id; }
    public String getPublicId() { return publicId; }
    public Long getStudentId() { return studentId; }
    public Long getOrganizationId() { return organizationId; }
    public Long getProfessionalProfileId() { return professionalProfileId; }
    public Long getCertificationTechnologyId() { return certificationTechnologyId; }
    public LocalDate getEnrollmentDate() { return enrollmentDate; }
    public String getTechnologicalProfile() { return technologicalProfile; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
