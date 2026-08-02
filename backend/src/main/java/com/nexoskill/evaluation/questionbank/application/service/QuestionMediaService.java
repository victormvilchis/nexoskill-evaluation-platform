package com.nexoskill.evaluation.questionbank.application.service;

import com.nexoskill.evaluation.organizations.domain.model.ContentScope;
import com.nexoskill.evaluation.organizations.domain.model.TenantContext;
import com.nexoskill.evaluation.questionbank.application.model.QuestionMediaView;
import com.nexoskill.evaluation.questionbank.application.port.out.QuestionMediaPort;
import com.nexoskill.evaluation.questionbank.application.port.out.QuestionMediaStoragePort;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuestionMediaService {
    private static final Logger LOGGER = LoggerFactory.getLogger(QuestionMediaService.class);
    private static final long MAX = 5L * 1024 * 1024;
    private static final Set<String> TYPES = Set.of("image/png", "image/jpeg", "image/webp", "image/gif");
    private final QuestionMediaPort media;
    private final QuestionMediaStoragePort storage;

    public QuestionMediaService(QuestionMediaPort media, QuestionMediaStoragePort storage) {
        this.media = media;
        this.storage = storage;
    }

    @Transactional
    public QuestionMediaView upload(String name, String type, byte[] content, Long actor, TenantContext tenant) {
        if (tenant == null || !tenant.hasOrganization()) {
            throw new BusinessException("QUESTION_MEDIA_CONTEXT_REQUIRED",
                    "No fue posible determinar la organización para guardar la imagen.");
        }
        if (content == null || content.length == 0) {
            throw new BusinessException("QUESTION_MEDIA_EMPTY", "Selecciona una imagen.");
        }
        if (content.length > MAX) {
            throw new BusinessException("QUESTION_MEDIA_TOO_LARGE", "La imagen no puede superar 5 MB.");
        }
        String normalized = type == null ? "" : type.toLowerCase();
        if (!TYPES.contains(normalized) || !signatureMatches(normalized, content)) {
            throw new BusinessException("QUESTION_MEDIA_TYPE_INVALID",
                    "El archivo no corresponde a una imagen PNG, JPG, WEBP o GIF válida.");
        }
        ContentScope scope = tenant.globalScope() ? ContentScope.GLOBAL : ContentScope.ORGANIZATION;
        String id = UUID.randomUUID().toString();
        String key = storage.save(id, normalized, content);
        try {
            return media.save(id, key, safeName(name), normalized, content.length, sha(content), scope,
                    tenant.organizationId(), actor);
        } catch (RuntimeException exception) {
            try {
                storage.delete(key);
            } catch (RuntimeException cleanupFailure) {
                LOGGER.error("Question media cleanup failed for storage key {}", key, cleanupFailure);
            }
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public Download download(String id, Long actorUserId, TenantContext tenant) {
        var data = media.getAuthorized(id, actorUserId, tenant);
        return new Download(data.view(), storage.read(data.storageKey()));
    }

    private boolean signatureMatches(String type, byte[] bytes) {
        if (type.equals("image/png"))
            return bytes.length > 8 && (bytes[0] & 255) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G';
        if (type.equals("image/jpeg"))
            return bytes.length > 3 && (bytes[0] & 255) == 0xFF && (bytes[1] & 255) == 0xD8 && (bytes[2] & 255) == 0xFF;
        if (type.equals("image/gif"))
            return bytes.length > 6 && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == '8';
        if (type.equals("image/webp"))
            return bytes.length > 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                    && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
        return false;
    }

    private String safeName(String name) {
        if (name == null || name.isBlank()) return "imagen";
        String value = name.replaceAll("[\\r\\n\\\\/]", "_").trim();
        return value.length() > 255 ? value.substring(0, 255) : value;
    }

    private String sha(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    public record Download(QuestionMediaView media, byte[] content) {
    }
}
