package com.nexoskill.evaluation.forms.domain;

import com.nexoskill.evaluation.shared.domain.BusinessException;
import java.util.Locale;

public enum FormContentMode {
    MANUAL,
    RANDOM_POOL;

    public static FormContentMode parse(String value) {
        if (value == null || value.isBlank()) {
            return MANUAL;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("FORM_CONTENT_MODE_INVALID",
                    "La modalidad de contenido indicada no es válida.");
        }
    }
}
