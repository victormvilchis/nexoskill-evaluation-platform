package com.nexoskill.evaluation.questionbank.application.port.out;

public interface QuestionMediaStoragePort {
	String save(String publicId, String contentType, byte[] content);

	byte[] read(String storageKey);

	void delete(String storageKey);
}
