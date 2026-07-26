package com.nexoskill.evaluation.collections.application;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class LearningCollectionModels {

	private LearningCollectionModels() {
	}

	public record CollectionCommand(String name, String description, List<String> formPublicIds, Long version) {
	}

	public record CollectionSummary(String publicId, String code, String name, String description, String status,
			int levelCount, int activeLevelCount, OffsetDateTime updatedAt, Long version) {
	}

	public record CollectionDetail(String publicId, String code, String name, String description, String status,
			List<LevelView> levels, OffsetDateTime createdAt, OffsetDateTime updatedAt, Long version) {
	}

	public record LevelView(int level, String unlockRule, String formPublicId, String formCode, String formTitle,
			String formStatus, String modeCode, BigDecimal passingScore, Integer durationMinutes) {
	}

	public record FormOption(String publicId, String code, String title, String status, String modeCode,
			BigDecimal passingScore, Integer durationMinutes) {
	}
}
