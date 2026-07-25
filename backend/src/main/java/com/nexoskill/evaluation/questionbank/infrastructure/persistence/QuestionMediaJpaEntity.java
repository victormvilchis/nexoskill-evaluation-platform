package com.nexoskill.evaluation.questionbank.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "QUESTION_MEDIA")
public class QuestionMediaJpaEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "MEDIA_ID")
	private Long id;
	@Column(name = "PUBLIC_ID", nullable = false, unique = true, length = 36)
	private String publicId;
	@Column(name = "STORAGE_KEY", nullable = false, unique = true, length = 500)
	private String storageKey;
	@Column(name = "ORIGINAL_NAME", nullable = false, length = 255)
	private String originalName;
	@Column(name = "CONTENT_TYPE", nullable = false, length = 100)
	private String contentType;
	@Column(name = "FILE_SIZE", nullable = false)
	private long size;
	@Column(name = "CHECKSUM_SHA256", nullable = false, length = 64)
	private String checksum;
	@Column(name = "CREATED_BY", nullable = false)
	private Long createdBy;
	@Column(name = "CREATED_AT", nullable = false)
	private Instant createdAt;

	protected QuestionMediaJpaEntity() {
	}

	public static QuestionMediaJpaEntity create(String p, String k, String n, String t, long s, String c, Long a,
			Instant now) {
		var e = new QuestionMediaJpaEntity();
		e.publicId = p;
		e.storageKey = k;
		e.originalName = n;
		e.contentType = t;
		e.size = s;
		e.checksum = c;
		e.createdBy = a;
		e.createdAt = now;
		return e;
	}

	public Long getId() {
		return id;
	}

	public String getPublicId() {
		return publicId;
	}

	public String getStorageKey() {
		return storageKey;
	}

	public String getOriginalName() {
		return originalName;
	}

	public String getContentType() {
		return contentType;
	}

	public long getSize() {
		return size;
	}
}
