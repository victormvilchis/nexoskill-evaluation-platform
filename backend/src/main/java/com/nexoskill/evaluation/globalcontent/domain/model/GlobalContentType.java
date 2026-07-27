package com.nexoskill.evaluation.globalcontent.domain.model;

import com.nexoskill.evaluation.shared.domain.BusinessException;
import java.util.Locale;

public enum GlobalContentType {
    CATEGORY,
    QUESTION,
    FORM,
    COLLECTION,
    PATH;

    public static GlobalContentType parse(String value) {
        try {
            return valueOf(value == null ? "" : value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("GLOBAL_CONTENT_TYPE_INVALID", "El tipo de contenido indicado no es válido.");
        }
    }
}
