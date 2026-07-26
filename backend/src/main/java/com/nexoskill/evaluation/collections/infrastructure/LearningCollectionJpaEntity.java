package com.nexoskill.evaluation.collections.infrastructure;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
}
