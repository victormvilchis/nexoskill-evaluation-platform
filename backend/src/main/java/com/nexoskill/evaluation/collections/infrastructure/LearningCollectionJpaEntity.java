package com.nexoskill.evaluation.collections.infrastructure;

import com.nexoskill.evaluation.globalcontent.domain.model.SyncStatus;
import com.nexoskill.evaluation.organizations.domain.model.ContentScope;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "LEARNING_COLLECTION")
public class LearningCollectionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "COLLECTION_ID")
    public Long id;

    @Column(name = "PUBLIC_ID", nullable = false, length = 36)
    public String publicId;

    @Column(name = "COLLECTION_CODE", nullable = false, length = 80)
    public String code;

    @Column(name = "COLLECTION_NAME", nullable = false, length = 200)
    public String name;

    @Column(name = "DESCRIPTION", length = 2000)
    public String description;

    @Column(name = "STATUS", nullable = false, length = 20)
    public String status;

    @Enumerated(EnumType.STRING)
    @Column(name = "CONTENT_SCOPE", nullable = false, length = 20)
    public ContentScope contentScope;

    @Column(name = "OWNER_ORGANIZATION_ID")
    public Long ownerOrganizationId;

    @Column(name = "SOURCE_GLOBAL_ID")
    public Long sourceGlobalId;

    @Column(name = "SOURCE_GLOBAL_VERSION")
    public Long sourceGlobalVersion;

    @Column(name = "IS_CUSTOMIZED", nullable = false)
    public Integer customized;

    @Column(name = "LAST_SYNCHRONIZED_AT")
    public OffsetDateTime lastSynchronizedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "SYNC_STATUS", nullable = false, length = 20)
    public SyncStatus syncStatus;

    @Column(name = "CREATED_BY")
    public Long createdBy;

    @Column(name = "UPDATED_BY")
    public Long updatedBy;

    @Column(name = "CREATED_AT", nullable = false)
    public OffsetDateTime createdAt;

    @Column(name = "UPDATED_AT", nullable = false)
    public OffsetDateTime updatedAt;

    @Version
    @Column(name = "VERSION_NO", nullable = false)
    public Long version;

    @OneToMany(mappedBy = "collection", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("levelOrder ASC")
    public List<LearningCollectionLevelJpaEntity> levels = new ArrayList<>();

    public LearningCollectionJpaEntity() {
    }

    public void replaceLevels(List<LearningCollectionLevelJpaEntity> newLevels) {
        levels.clear();
        for (LearningCollectionLevelJpaEntity level : newLevels) {
            level.collection = this;
            levels.add(level);
        }
    }

    public void assignOwnership(ContentScope scope, Long organizationId) {
        if (scope == null || organizationId == null) {
            throw new IllegalArgumentException("El contenido requiere una organización propietaria.");
        }
        this.contentScope = scope;
        this.ownerOrganizationId = organizationId;
        this.customized = 0;
        this.syncStatus = SyncStatus.NOT_LINKED;
    }

    public void markCustomized(OffsetDateTime now) {
        if (sourceGlobalId != null) {
            customized = 1;
            syncStatus = SyncStatus.DIVERGED;
            lastSynchronizedAt = now;
        }
    }
}
