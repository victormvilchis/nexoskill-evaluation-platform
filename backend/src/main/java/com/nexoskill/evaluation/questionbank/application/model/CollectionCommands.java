package com.nexoskill.evaluation.questionbank.application.model;

import com.nexoskill.evaluation.questionbank.domain.model.CollectionStatus;
import java.util.List;

public final class CollectionCommands {
	private CollectionCommands() {
	}

	public record Create(String name, String description, List<String> categoryPublicIds,
			List<String> questionPublicIds, Long actorUserId) {
		public Create {
			categoryPublicIds = categoryPublicIds == null ? List.of() : List.copyOf(categoryPublicIds);
			questionPublicIds = questionPublicIds == null ? List.of() : List.copyOf(questionPublicIds);
		}
	}

	public record Update(String publicId, String name, String description, List<String> categoryPublicIds,
			List<String> questionPublicIds, long expectedEntityVersion, Long actorUserId) {
		public Update {
			categoryPublicIds = categoryPublicIds == null ? List.of() : List.copyOf(categoryPublicIds);
			questionPublicIds = questionPublicIds == null ? List.of() : List.copyOf(questionPublicIds);
		}
	}

	public record ChangeStatus(String publicId, CollectionStatus status, long expectedEntityVersion, Long actorUserId) {
	}
}
