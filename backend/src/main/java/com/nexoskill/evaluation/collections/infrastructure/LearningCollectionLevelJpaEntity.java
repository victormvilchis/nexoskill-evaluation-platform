package com.nexoskill.evaluation.collections.infrastructure;

import com.nexoskill.evaluation.forms.infrastructure.FormJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "LEARNING_COLLECTION_LEVEL")
public class LearningCollectionLevelJpaEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "LEVEL_ID")
	public Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "COLLECTION_ID", nullable = false)
	public LearningCollectionJpaEntity collection;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "FORM_ID", nullable = false)
	public FormJpaEntity form;

	@Column(name = "LEVEL_ORDER", nullable = false)
	public Integer levelOrder;

	@Column(name = "UNLOCK_RULE", nullable = false, length = 30)
	public String unlockRule;

	@Column(name = "CREATED_AT", nullable = false)
	public OffsetDateTime createdAt;

	public LearningCollectionLevelJpaEntity() {
	}
}
