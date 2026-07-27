package com.nexoskill.evaluation.questionbank.application.service;

import com.nexoskill.evaluation.shared.domain.BusinessException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class QuestionTagNormalizer {
    public static final int MAX_TAGS = 10;
    public static final int MAX_LENGTH = 40;

    private static final Pattern MULTIPLE_SPACES = Pattern.compile("\\s+");
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern ALLOWED = Pattern.compile("[\\p{L}\\p{N}_\\- ]+");
    private static final Pattern MULTIPLE_HYPHENS = Pattern.compile("-+");

    private QuestionTagNormalizer() {
    }

    public static List<NormalizedTag> normalizeAll(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        if (values.size() > MAX_TAGS) {
            throw new BusinessException("QUESTION_TAG_LIMIT_EXCEEDED",
                    "Puedes agregar un máximo de 10 etiquetas.");
        }

        LinkedHashMap<String, NormalizedTag> unique = new LinkedHashMap<>();
        for (String value : values) {
            NormalizedTag tag = normalize(value);
            unique.putIfAbsent(tag.normalizedName(), tag);
        }
        if (unique.size() > MAX_TAGS) {
            throw new BusinessException("QUESTION_TAG_LIMIT_EXCEEDED",
                    "Puedes agregar un máximo de 10 etiquetas.");
        }
        return List.copyOf(new ArrayList<>(unique.values()));
    }

    public static NormalizedTag normalize(String value) {
        String displayName = value == null ? "" : value.trim();
        while (displayName.startsWith("#")) {
            displayName = displayName.substring(1).trim();
        }
        displayName = MULTIPLE_SPACES.matcher(displayName).replaceAll(" ");

        if (displayName.isBlank()) {
            throw new BusinessException("QUESTION_TAG_EMPTY", "La etiqueta no puede estar vacía.");
        }
        if (displayName.length() > MAX_LENGTH) {
            throw new BusinessException("QUESTION_TAG_TOO_LONG",
                    "Cada etiqueta puede tener hasta 40 caracteres.");
        }
        if (!ALLOWED.matcher(displayName).matches()) {
            throw new BusinessException("QUESTION_TAG_INVALID",
                    "La etiqueta contiene símbolos no permitidos.");
        }

        String normalizedName = searchValue(displayName);
        String slug = MULTIPLE_HYPHENS.matcher(
                normalizedName.replace('_', '-').replace(' ', '-'))
                .replaceAll("-");
        if (slug.isBlank()) {
            throw new BusinessException("QUESTION_TAG_INVALID", "La etiqueta indicada no es válida.");
        }
        return new NormalizedTag(displayName, normalizedName, slug);
    }

    public static String searchValue(String value) {
        if (value == null) {
            return "";
        }
        String stripped = value.trim();
        while (stripped.startsWith("#")) {
            stripped = stripped.substring(1).trim();
        }
        stripped = MULTIPLE_SPACES.matcher(stripped).replaceAll(" ").toLowerCase(Locale.ROOT);
        return DIACRITICS.matcher(Normalizer.normalize(stripped, Normalizer.Form.NFD))
                .replaceAll("");
    }

    public record NormalizedTag(String displayName, String normalizedName, String slug) {
    }
}
