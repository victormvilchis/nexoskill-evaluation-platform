package com.nexoskill.evaluation.questionbank.application.model;

import com.nexoskill.evaluation.questionbank.domain.model.CatalogStatus;

public final class CategoryCommands {
	private CategoryCommands() {
	}

	public record Create(String code, String name, String description, Long actorUserId) {
	}

	public record Update(String publicId, String code, String name, String description, long expectedEntityVersion,
			Long actorUserId) {
	}

	public record ChangeStatus(String publicId, CatalogStatus status, long expectedEntityVersion, Long actorUserId) {
	}
}
