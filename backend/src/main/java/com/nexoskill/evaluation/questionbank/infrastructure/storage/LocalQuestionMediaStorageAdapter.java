package com.nexoskill.evaluation.questionbank.infrastructure.storage;

import com.nexoskill.evaluation.questionbank.application.port.out.QuestionMediaStoragePort;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import java.nio.file.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LocalQuestionMediaStorageAdapter implements QuestionMediaStoragePort {
	private final Path root;

	public LocalQuestionMediaStorageAdapter(
			@Value("${app.storage.question-media-root:./storage/question-media}") String root) {
		this.root = Path.of(root).toAbsolutePath().normalize();
	}

	public String save(String id, String type, byte[] content) {
		String ext = switch (type.toLowerCase()) {
		case "image/png" -> "png";
		case "image/jpeg" -> "jpg";
		case "image/webp" -> "webp";
		case "image/gif" -> "gif";
		default -> "bin";
		};
		String key = id.substring(0, 2) + "/" + id + "." + ext;
		Path target = root.resolve(key).normalize();
		if (!target.startsWith(root))
			throw new IllegalStateException("Ruta inválida");
		try {
			Files.createDirectories(target.getParent());
			Files.write(target, content, StandardOpenOption.CREATE_NEW);
			return key;
		} catch (Exception e) {
			throw new BusinessException("QUESTION_MEDIA_STORAGE_ERROR", "No fue posible guardar la imagen.");
		}
	}

	public byte[] read(String key) {
		Path p = root.resolve(key).normalize();
		if (!p.startsWith(root))
			throw new IllegalStateException("Ruta inválida");
		try {
			return Files.readAllBytes(p);
		} catch (Exception e) {
			throw new BusinessException("QUESTION_MEDIA_READ_ERROR", "No fue posible leer la imagen.");
		}
	}

	public void delete(String key) {
		try {
			Files.deleteIfExists(root.resolve(key).normalize());
		} catch (Exception ignored) {
		}
	}
}
