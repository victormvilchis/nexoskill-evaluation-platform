package com.nexoskill.evaluation.questionbank.infrastructure.persistence;

import com.nexoskill.evaluation.questionbank.domain.model.CollectionStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "QUESTION_COLLECTION")
public class QuestionCollectionJpaEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "COLLECTION_ID")
	private Long id;
	@Column(name = "PUBLIC_ID", nullable = false, unique = true, length = 36)
	private String publicId;
	@Column(name = "COLLECTION_NAME", nullable = false, length = 180)
	private String name;
	@Column(name = "NORMALIZED_NAME", nullable = false, unique = true, length = 180)
	private String normalizedName;
	@Column(name = "DESCRIPTION", length = 1000)
	private String description;
	@Enumerated(EnumType.STRING)
	@Column(name = "STATUS", nullable = false, length = 20)
	private CollectionStatus status;
	@ManyToMany(fetch = FetchType.EAGER)
	@JoinTable(name = "COLLECTION_CATEGORY_RELATION", joinColumns = @JoinColumn(name = "COLLECTION_ID"), inverseJoinColumns = @JoinColumn(name = "CATEGORY_ID"))
	private Set<QuestionCategoryJpaEntity> categories = new LinkedHashSet<>();
	@ManyToMany(fetch = FetchType.EAGER)
	@JoinTable(name = "COLLECTION_QUESTION_RELATION", joinColumns = @JoinColumn(name = "COLLECTION_ID"), inverseJoinColumns = @JoinColumn(name = "QUESTION_ID"))
	private Set<QuestionJpaEntity> questions = new LinkedHashSet<>();
	@Column(name = "CREATED_BY", nullable = false)
	private Long createdBy;
	@Column(name = "CREATED_AT", nullable = false)
	private Instant createdAt;
	@Column(name = "UPDATED_BY")
	private Long updatedBy;
	@Column(name = "UPDATED_AT")
	private Instant updatedAt;
	@Version
	@Column(name = "VERSION_NO", nullable = false)
	private long version;

	protected QuestionCollectionJpaEntity() {
	}

	public static QuestionCollectionJpaEntity create(String id, String name, String norm, String desc,
			Set<QuestionCategoryJpaEntity> cats, Set<QuestionJpaEntity> qs, Long actor, Instant now) {
		var e = new QuestionCollectionJpaEntity();
		e.publicId = id;
		e.status = CollectionStatus.ACTIVE;
		e.createdBy = actor;
		e.createdAt = now;
		e.apply(name, norm, desc, cats, qs, actor, now);
		return e;
	}

	public void apply(String name, String norm, String desc, Set<QuestionCategoryJpaEntity> cats,
			Set<QuestionJpaEntity> qs, Long actor, Instant now) {
		this.name = name;
		this.normalizedName = norm;
		this.description = desc;
		categories.clear();
		categories.addAll(cats);
		questions.clear();
		questions.addAll(qs);
		updatedBy = actor;
		updatedAt = now;
	}

	public void changeStatus(CollectionStatus s, Long actor, Instant now) {
		status = s;
		updatedBy = actor;
		updatedAt = now;
	}

	public Long getId() {
		return id;
	}

	public String getPublicId() {
		return publicId;
	}

	public String getName() {
		return name;
	}

	public String getNormalizedName() {
		return normalizedName;
	}

	public String getDescription() {
		return description;
	}

	public CollectionStatus getStatus() {
		return status;
	}

	public Set<QuestionCategoryJpaEntity> getCategories() {
		return Set.copyOf(categories);
	}

	public Set<QuestionJpaEntity> getQuestions() {
		return Set.copyOf(questions);
	}

	public long getVersion() {
		return version;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
