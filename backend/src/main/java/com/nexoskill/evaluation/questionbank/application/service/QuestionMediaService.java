package com.nexoskill.evaluation.questionbank.application.service;

import com.nexoskill.evaluation.questionbank.application.model.QuestionMediaView;
import com.nexoskill.evaluation.questionbank.application.port.out.*;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuestionMediaService {
	private static final long MAX = 5L * 1024 * 1024;
	private static final Set<String> TYPES = Set.of("image/png", "image/jpeg", "image/webp", "image/gif");
	private final QuestionMediaPort media;
	private final QuestionMediaStoragePort storage;

	public QuestionMediaService(QuestionMediaPort m, QuestionMediaStoragePort s) {
		media = m;
		storage = s;
	}

	@Transactional
	public QuestionMediaView upload(String name, String type, byte[] content, Long actor) {
		if (content == null || content.length == 0)
			throw new BusinessException("QUESTION_MEDIA_EMPTY", "Selecciona una imagen.");
		if (content.length > MAX)
			throw new BusinessException("QUESTION_MEDIA_TOO_LARGE", "La imagen no puede superar 5 MB.");
		String normalized = type == null ? "" : type.toLowerCase();
		if (!TYPES.contains(normalized) || !signatureMatches(normalized, content))
			throw new BusinessException("QUESTION_MEDIA_TYPE_INVALID",
					"El archivo no corresponde a una imagen PNG, JPG, WEBP o GIF válida.");
		String id = UUID.randomUUID().toString();
		String key = storage.save(id, normalized, content);
		try {
			return media.save(id, key, safeName(name), normalized, content.length, sha(content), actor);
		} catch (RuntimeException e) {
			storage.delete(key);
			throw e;
		}
	}

	@Transactional(readOnly = true)
	public Download download(String id) {
		var d = media.get(id);
		return new Download(d.view(), storage.read(d.storageKey()));
	}

	private boolean signatureMatches(String type, byte[] b) {
		if (type.equals("image/png"))
			return b.length > 8 && (b[0] & 255) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
		if (type.equals("image/jpeg"))
			return b.length > 3 && (b[0] & 255) == 0xFF && (b[1] & 255) == 0xD8 && (b[2] & 255) == 0xFF;
		if (type.equals("image/gif"))
			return b.length > 6 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8';
		if (type.equals("image/webp"))
			return b.length > 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' && b[8] == 'W'
					&& b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
		return false;
	}

	private String safeName(String n) {
		if (n == null || n.isBlank())
			return "imagen";
		String v = n.replaceAll("[\\r\\n\\\\/]", "_").trim();
		return v.length() > 255 ? v.substring(0, 255) : v;
	}

	private String sha(byte[] b) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	public record Download(QuestionMediaView media, byte[] content) {
	}
}
