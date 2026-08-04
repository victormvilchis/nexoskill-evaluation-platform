package com.nexoskill.evaluation.catalogs.application;

import com.nexoskill.evaluation.catalogs.domain.CatalogType;
import java.time.Instant;
import java.util.List;

public final class CatalogModels {
	private CatalogModels() {
	}

	public record TypeSummary(CatalogType type, String name, String description, long activeCount, long inactiveCount,
			Instant lastModifiedAt, boolean tenantAware) {
	}

	public record Item(String id, String code, String name, String description, String status, int displayOrder,
			String scope, String organizationPublicId, String organizationName, String suggestedTechnologicalProfile,
			Long createdBy, Long updatedBy, Instant createdAt, Instant updatedAt, Long version, long dependencyCount) {
	}

	public record Dependencies(long total, List<String> details, boolean deletable) {
	}

	public record Upsert(String code, String name, String description, Integer displayOrder,
			String organizationPublicId, String suggestedTechnologicalProfile, Long expectedVersion) {
	}
}
