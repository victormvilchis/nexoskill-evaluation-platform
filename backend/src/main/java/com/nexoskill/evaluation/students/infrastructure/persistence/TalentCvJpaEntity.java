package com.nexoskill.evaluation.students.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "TALENT_CV_DOCUMENT")
public class TalentCvJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "TALENT_CV_DOCUMENT_ID")
    private Long id;

    @Column(name = "PUBLIC_ID", nullable = false, unique = true, length = 36)
    private String publicId;

    @Column(name = "STUDENT_ID", nullable = false, unique = true)
    private Long studentId;

    @Column(name = "ORGANIZATION_ID", nullable = false)
    private Long organizationId;

    @Column(name = "FILE_NAME", nullable = false, length = 255)
    private String fileName;

    @Column(name = "CONTENT_TYPE", nullable = false, length = 150)
    private String contentType;

    @Column(name = "FILE_EXTENSION", nullable = false, length = 10)
    private String fileExtension;

    @Column(name = "FILE_SIZE", nullable = false)
    private long fileSize;

    @Lob
    @Column(name = "FILE_DATA", nullable = false)
    private byte[] fileData;

    @Column(name = "UPLOADED_BY", nullable = false)
    private Long uploadedBy;

    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "VERSION_NO", nullable = false)
    private Long version;

    protected TalentCvJpaEntity() {}

    public static TalentCvJpaEntity create(String publicId, Long studentId, Long organizationId,
            String fileName, String contentType, String extension, byte[] data, Long actorId, Instant now) {
        TalentCvJpaEntity entity = new TalentCvJpaEntity();
        entity.publicId = publicId;
        entity.studentId = studentId;
        entity.organizationId = organizationId;
        entity.replace(fileName, contentType, extension, data, actorId, now);
        entity.createdAt = now;
        entity.version = 0L;
        return entity;
    }

    public void replace(String fileName, String contentType, String extension, byte[] data,
            Long actorId, Instant now) {
        this.fileName = fileName;
        this.contentType = contentType;
        this.fileExtension = extension;
        this.fileData = data == null ? new byte[0] : data.clone();
        this.fileSize = this.fileData.length;
        this.uploadedBy = actorId;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public String getPublicId() { return publicId; }
    public Long getStudentId() { return studentId; }
    public Long getOrganizationId() { return organizationId; }
    public String getFileName() { return fileName; }
    public String getContentType() { return contentType; }
    public String getFileExtension() { return fileExtension; }
    public long getFileSize() { return fileSize; }
    public byte[] getFileData() { return fileData == null ? new byte[0] : fileData.clone(); }
    public Long getUploadedBy() { return uploadedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
