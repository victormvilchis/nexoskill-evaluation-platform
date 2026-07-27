package com.nexoskill.evaluation.questionbank.infrastructure.persistence;

import com.nexoskill.evaluation.questionbank.domain.model.QuestionTechnologyStatus;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "QUESTION_TECHNOLOGY")
public class QuestionTechnologyJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "TECHNOLOGY_ID")
    private Long id;

    @Column(name = "PUBLIC_ID", nullable = false, unique = true, length = 36)
    private String publicId;

    @Column(name = "TECHNOLOGY_CODE", nullable = false, unique = true, length = 80)
    private String code;

    @Column(name = "TECHNOLOGY_NAME", nullable = false, length = 160)
    private String name;

    @Column(name = "DESCRIPTION", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private QuestionTechnologyStatus status;

    @Column(name = "DISPLAY_ORDER", nullable = false)
    private int displayOrder;

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

    protected QuestionTechnologyJpaEntity() { }

    public static QuestionTechnologyJpaEntity create(String publicId, String code, String name,
            String description, int displayOrder, Long actorId, Instant now) {
        QuestionTechnologyJpaEntity entity = new QuestionTechnologyJpaEntity();
        entity.publicId = publicId;
        entity.code = code;
        entity.name = name;
        entity.description = description;
        entity.status = QuestionTechnologyStatus.ACTIVE;
        entity.displayOrder = displayOrder;
        entity.createdBy = actorId;
        entity.updatedBy = actorId;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public void update(String name, String description, int displayOrder, Long actorId, Instant now) {
        this.name = name;
        this.description = description;
        this.displayOrder = displayOrder;
        this.updatedBy = actorId;
        this.updatedAt = now;
    }

    public void changeStatus(QuestionTechnologyStatus status, Long actorId, Instant now) {
        this.status = status;
        this.updatedBy = actorId;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public String getPublicId() { return publicId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public QuestionTechnologyStatus getStatus() { return status; }
    public int getDisplayOrder() { return displayOrder; }
    public Long getCreatedBy() { return createdBy; }
    public Long getUpdatedBy() { return updatedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
