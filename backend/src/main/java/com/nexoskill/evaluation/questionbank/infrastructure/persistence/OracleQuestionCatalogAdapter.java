package com.nexoskill.evaluation.questionbank.infrastructure.persistence;

import com.nexoskill.evaluation.questionbank.application.model.*;
import com.nexoskill.evaluation.questionbank.application.port.out.QuestionCatalogPort;
import com.nexoskill.evaluation.questionbank.domain.model.CatalogStatus;
import com.nexoskill.evaluation.shared.domain.*;
import java.text.Normalizer;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class OracleQuestionCatalogAdapter implements QuestionCatalogPort {
	private final SpringDataQuestionTypeRepository types;
	private final SpringDataQuestionDifficultyRepository difficulties;
	private final SpringDataQuestionCategoryRepository categories;
	private final SpringDataQuestionRepository questions;
	private final Clock clock;

	public OracleQuestionCatalogAdapter(SpringDataQuestionTypeRepository t, SpringDataQuestionDifficultyRepository d,
			SpringDataQuestionCategoryRepository c, SpringDataQuestionRepository q, Clock clock) {
		types = t;
		difficulties = d;
		categories = c;
		questions = q;
		this.clock = clock;
	}

	public QuestionCatalogs activeCatalogs() {
		return new QuestionCatalogs(
				types.findAllByStatusOrderByNameAsc(CatalogStatus.ACTIVE).stream()
						.map(t -> new CatalogOption(t.getCode(), t.getName(), t.getDescription())).toList(),
				difficulties.findAllByStatusOrderBySortOrderAsc(CatalogStatus.ACTIVE).stream()
						.map(d -> new CatalogOption(d.getCode(), d.getName(), null)).toList(),
				categories.findAllByStatusOrderByNameAsc(CatalogStatus.ACTIVE).stream().map(this::summary).toList());
	}

	public List<QuestionCategorySummary> categories() {
		return categories.findAllByOrderByNameAsc().stream().map(this::summary).toList();
	}

	public QuestionCategorySummary create(CategoryCommands.Create c) {
		String name = name(c.name());
		String code = code(c.code(), name);
		ensureUnique(code, name, null);
		return summary(categories.saveAndFlush(QuestionCategoryJpaEntity.create(UUID.randomUUID().toString(), code,
				name, nullable(c.description()), c.actorUserId(), clock.instant())));
	}

	public QuestionCategorySummary update(CategoryCommands.Update c) {
		String id = PublicIdNormalizer.requiredUuid(c.publicId(), "CATEGORY_ID_INVALID",
				"La categoría indicada no es válida.");
		var e = categories.findByPublicId(id)
				.orElseThrow(() -> error("CATEGORY_NOT_FOUND", "La categoría solicitada no existe."));
		if (e.getVersion() != c.expectedEntityVersion())
			throw error("CATEGORY_CONCURRENT_MODIFICATION", "La categoría fue modificada por otra persona.");
		String name = name(c.name()), code = code(c.code(), name);
		ensureUnique(code, name, id);
		e.update(code, name, nullable(c.description()), c.actorUserId(), clock.instant());
		return summary(categories.saveAndFlush(e));
	}

	public QuestionCategorySummary changeStatus(CategoryCommands.ChangeStatus c) {
		String id = PublicIdNormalizer.requiredUuid(c.publicId(), "CATEGORY_ID_INVALID",
				"La categoría indicada no es válida.");
		var e = categories.findByPublicId(id)
				.orElseThrow(() -> error("CATEGORY_NOT_FOUND", "La categoría solicitada no existe."));
		if (e.getVersion() != c.expectedEntityVersion())
			throw error("CATEGORY_CONCURRENT_MODIFICATION",
					"La categoría fue modificada por otra persona. Recarga la información.");
		e.changeStatus(c.status(), c.actorUserId(), clock.instant());
		return summary(categories.saveAndFlush(e));
	}

	private void ensureUnique(String code, String name, String ignore) {
		for (var e : categories.findAll()) {
			if (ignore != null && e.getPublicId().equals(ignore))
				continue;
			if (e.getCode().equalsIgnoreCase(code))
				throw error("CATEGORY_CODE_ALREADY_EXISTS", "Ya existe una categoría con ese código.");
			if (normalize(e.getName()).equals(normalize(name)))
				throw error("CATEGORY_ALREADY_EXISTS", "Ya existe una categoría con ese nombre.");
		}
	}

	private QuestionCategorySummary summary(QuestionCategoryJpaEntity e) {
		return new QuestionCategorySummary(e.getPublicId(), e.getCode(), e.getName(), e.getDescription(), e.getStatus(),
				e.getVersion(), questions.countActiveByCategory(e.getId()), e.getCreatedAt(), e.getUpdatedAt());
	}

	private String name(String v) {
		if (v == null || v.isBlank())
			throw error("CATEGORY_NAME_REQUIRED", "El nombre es obligatorio.");
		String n = v.trim().replaceAll("\\s+", " ");
		if (n.length() > 150)
			throw error("CATEGORY_NAME_TOO_LONG", "El nombre no puede superar 150 caracteres.");
		return n;
	}

	private String code(String v, String n) {
		String source = v == null || v.isBlank() ? n : v;
		String x = Normalizer.normalize(source, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT)
				.replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
		if (x.isBlank())
			throw error("CATEGORY_CODE_INVALID", "No fue posible generar un código válido.");
		return x.length() > 80 ? x.substring(0, 80) : x;
	}

	private String normalize(String v) {
		return Normalizer.normalize(v, Normalizer.Form.NFKC).replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
	}

	private String nullable(String v) {
		return v == null || v.isBlank() ? null : v.trim();
	}

	private BusinessException error(String c, String m) {
		return new BusinessException(c, m);
	}
}
