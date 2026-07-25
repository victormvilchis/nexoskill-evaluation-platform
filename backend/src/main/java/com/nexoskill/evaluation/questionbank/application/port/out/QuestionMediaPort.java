package com.nexoskill.evaluation.questionbank.application.port.out;

import com.nexoskill.evaluation.questionbank.application.model.QuestionMediaView;

public interface QuestionMediaPort {
	QuestionMediaView save(String publicId, String storageKey, String originalName, String contentType, long size,
			String checksum, Long actorUserId);

	MediaData get(String publicId);

	record MediaData(QuestionMediaView view, String storageKey) {
	}
}
