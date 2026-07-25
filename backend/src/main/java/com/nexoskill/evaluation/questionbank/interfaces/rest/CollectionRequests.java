package com.nexoskill.evaluation.questionbank.interfaces.rest;

import jakarta.validation.constraints.*;
import java.util.List;

public final class CollectionRequests {
	private CollectionRequests() {
	}

	public record Create(@NotBlank @Size(max = 180) String name, @Size(max = 1000) String description,
			List<String> categoryPublicIds, List<String> questionPublicIds) {
	}

	public record Update(@NotBlank @Size(max = 180) String name, @Size(max = 1000) String description,
			List<String> categoryPublicIds, List<String> questionPublicIds,
			@PositiveOrZero long expectedEntityVersion) {
	}

	public record Status(@PositiveOrZero long expectedEntityVersion) {
	}
}
