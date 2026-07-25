package com.nexoskill.evaluation.questionbank.infrastructure.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexoskill.evaluation.questionbank.application.model.*;
import com.nexoskill.evaluation.questionbank.application.port.out.*;
import com.nexoskill.evaluation.questionbank.domain.model.*;
import com.nexoskill.evaluation.shared.domain.*;
import java.time.Clock;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Component;

@Component
public class OracleQuestionBankAdapter implements QuestionBankPort {
	private final SpringDataQuestionRepository questions;
	private final SpringDataQuestionOptionRepository options;
	private final SpringDataQuestionTypeRepository types;
	private final SpringDataQuestionDifficultyRepository difficulties;
	private final SpringDataQuestionCategoryRepository categories;
	private final SpringDataQuestionMediaRepository media;
	private final QuestionUsageChecker usage;
	private final ObjectMapper json;
	private final Clock clock;

	public OracleQuestionBankAdapter(SpringDataQuestionRepository q, SpringDataQuestionOptionRepository o,
			SpringDataQuestionTypeRepository t, SpringDataQuestionDifficultyRepository d,
			SpringDataQuestionCategoryRepository c, SpringDataQuestionMediaRepository m, QuestionUsageChecker u,
			ObjectMapper j, Clock clock) {
		questions = q;
		options = o;
		types = t;
		difficulties = d;
		categories = c;
		media = m;
		usage = u;
		json = j;
		this.clock = clock;
	}

	public QuestionDetail create(CreateQuestionCommand c) {
		var sel = selection(c.typeCode(), c.difficultyCode(), c.categoryPublicIds(), Set.of());
		var prompt = optionalMedia(c.promptMediaPublicId());
		var s = c.answerSettings();
		var e = QuestionJpaEntity.create(UUID.randomUUID().toString(), sel.type, sel.difficulty, sel.categories,
				trim(c.statement()), nullable(c.explanation()), prompt, code(c.codeLanguage()),
				nullable(c.codeContent()), writeAnswers(s.acceptedAnswers()), s.caseSensitive(),
				effectiveManual(sel.type.getCode(), s.manualReview()), s.numericMin(), s.numericMax(),
				s.numericTolerance(), s.maxLength(), c.actorUserId(), clock.instant());
		addOptions(e, c.options());
		return toDetail(questions.saveAndFlush(e));
	}

	public QuestionDetail update(UpdateQuestionCommand c) {
		var e = locked(c.publicId());
		checkVersion(e, c.expectedEntityVersion());
		if (e.getStatus() == QuestionStatus.ARCHIVED)
			throw error("QUESTION_ARCHIVED", "Reactiva la pregunta antes de editarla.");
		Set<String> existing = e.getCategories().stream().map(QuestionCategoryJpaEntity::getPublicId)
				.collect(java.util.stream.Collectors.toSet());
		var sel = selection(c.typeCode(), c.difficultyCode(), c.categoryPublicIds(), existing);
		var s = c.answerSettings();
		options.deleteByQuestionId(e.getId());
		options.flush();
		e.clearOptions();
		e.apply(sel.type, sel.difficulty, sel.categories, trim(c.statement()), nullable(c.explanation()),
				optionalMedia(c.promptMediaPublicId()), code(c.codeLanguage()), nullable(c.codeContent()),
				writeAnswers(s.acceptedAnswers()), s.caseSensitive(),
				effectiveManual(sel.type.getCode(), s.manualReview()), s.numericMin(), s.numericMax(),
				s.numericTolerance(), s.maxLength(), c.actorUserId(), clock.instant());
		addOptions(e, c.options());
		return toDetail(questions.saveAndFlush(e));
	}

	public QuestionDetail get(String id) {
		return toDetail(find(id));
	}

	public QuestionPage search(String query, QuestionStatus status, String type, String difficulty, String category,
			int page, int size) {
		String q = query == null || query.isBlank() ? null : query.trim().toLowerCase(Locale.ROOT);
		String t = norm(type), d = norm(difficulty),
				c = category == null || category.isBlank() ? null
						: PublicIdNormalizer.requiredUuid(category, "QUESTION_CATEGORY_INVALID",
								"La categoría indicada no es válida.");
		PageRequest pageable = PageRequest.of(page, size);
		Page<QuestionJpaEntity> p = q == null
				? questions.searchWithoutText(status == null ? null : status.name(), t, d, c, pageable)
				: questions.searchWithText(q, status == null ? null : status.name(), t, d, c, pageable);
		return new QuestionPage(p.getContent().stream().map(this::toSummary).toList(), p.getNumber(), p.getSize(),
				p.getTotalElements(), p.getTotalPages());
	}

	public QuestionDetail duplicate(String id, Long actor) {
		var src = find(id);
		var cats = src.getCategories();
		var e = QuestionJpaEntity.create(UUID.randomUUID().toString(), src.getType(), src.getDifficulty(),
				new LinkedHashSet<>(cats), src.getStatement() + " (copia)", src.getExplanation(), src.getPromptMedia(),
				src.getCodeLanguage(), src.getCodeContent(), src.getAcceptedAnswersJson(), src.isCaseSensitive(),
				src.isManualReview(), src.getNumericMin(), src.getNumericMax(), src.getNumericTolerance(),
				src.getResponseMaxLength(), actor, clock.instant());
		for (var o : src.getOptions())
			e.addOption(QuestionOptionJpaEntity.create(e, UUID.randomUUID().toString(), o.getOptionOrder(), o.getText(),
					o.getMedia(), o.isCorrect(), clock.instant()));
		return toDetail(questions.saveAndFlush(e));
	}

	public QuestionDetail changeStatus(String id, QuestionStatus status, long expected, Long actor) {
		var e = locked(id);
		checkVersion(e, expected);
		if (status == QuestionStatus.ARCHIVED && usage.isUsedByActiveExam(e.getId()))
			throw error("QUESTION_USED_BY_ACTIVE_EXAM",
					"No se puede archivar porque está incluida en una evaluación activa.");
		e.changeStatus(status, actor, clock.instant());
		return toDetail(questions.saveAndFlush(e));
	}

	private void addOptions(QuestionJpaEntity e, List<QuestionOptionCommand> commands) {
		int i = 1;
		for (var c : commands)
			e.addOption(QuestionOptionJpaEntity.create(e, UUID.randomUUID().toString(), i++, nullable(c.text()),
					optionalMedia(c.mediaPublicId()), c.correct(), clock.instant()));
	}

	private Selection selection(String type, String difficulty, List<String> ids, Set<String> existing) {
		var t = types.findByCodeAndStatus(norm(type), CatalogStatus.ACTIVE)
				.orElseThrow(() -> error("QUESTION_TYPE_INVALID", "El tipo de pregunta no está disponible."));
		var d = difficulties.findByCodeAndStatus(norm(difficulty), CatalogStatus.ACTIVE)
				.orElseThrow(() -> error("QUESTION_DIFFICULTY_INVALID", "La dificultad no está disponible."));
		Set<String> canonical = ids.stream().map(
				v -> PublicIdNormalizer.requiredUuid(v, "QUESTION_CATEGORY_INVALID", "Una categoría no es válida."))
				.collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
		if (canonical.isEmpty())
			throw error("QUESTION_CATEGORY_REQUIRED", "Selecciona al menos una categoría.");
		List<QuestionCategoryJpaEntity> found = categories.findAllByPublicIdIn(canonical);
		if (found.size() != canonical.size())
			throw error("CATEGORY_NOT_FOUND", "Una de las categorías seleccionadas no existe.");
		for (var c : found)
			if (c.getStatus() != CatalogStatus.ACTIVE && !existing.contains(c.getPublicId()))
				throw error("CATEGORY_INACTIVE", "La categoría " + c.getName() + " está inactiva.");
		return new Selection(t, d, new LinkedHashSet<>(found));
	}

	private QuestionJpaEntity find(String id) {
		String p = PublicIdNormalizer.requiredUuid(id, "QUESTION_ID_INVALID", "La pregunta indicada no es válida.");
		return questions.findByPublicId(p)
				.orElseThrow(() -> error("QUESTION_NOT_FOUND", "La pregunta solicitada no existe."));
	}

	private QuestionJpaEntity locked(String id) {
		String p = PublicIdNormalizer.requiredUuid(id, "QUESTION_ID_INVALID", "La pregunta indicada no es válida.");
		return questions.findByPublicIdForUpdate(p)
				.orElseThrow(() -> error("QUESTION_NOT_FOUND", "La pregunta solicitada no existe."));
	}

	private void checkVersion(QuestionJpaEntity e, long v) {
		if (e.getVersion() != v)
			throw error("QUESTION_CONCURRENT_MODIFICATION",
					"La pregunta fue modificada por otra persona. Recarga la información.");
	}

	private QuestionMediaJpaEntity optionalMedia(String id) {
		if (id == null || id.isBlank())
			return null;
		String p = PublicIdNormalizer.requiredUuid(id, "QUESTION_MEDIA_INVALID",
				"La imagen seleccionada no es válida.");
		return media.findByPublicId(p)
				.orElseThrow(() -> error("QUESTION_MEDIA_NOT_FOUND", "La imagen seleccionada no existe."));
	}

	private String writeAnswers(List<String> a) {
		try {
			return a == null || a.isEmpty() ? null : json.writeValueAsString(a);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private List<String> readAnswers(String a) {
		try {
			return a == null ? List.of() : json.readValue(a, new TypeReference<List<String>>() {
			});
		} catch (Exception e) {
			return List.of();
		}
	}

	private boolean effectiveManual(String type, boolean requested) {
		try {
			return QuestionTypeCode.valueOf(type).requiresManualReview() || requested;
		} catch (Exception e) {
			return requested;
		}
	}

	private QuestionDetail toDetail(QuestionJpaEntity e) {
		return new QuestionDetail(e.getPublicId(), e.getStatement(), e.getExplanation(), e.getType().getCode(),
				e.getType().getName(), e.getDifficulty().getCode(), e.getDifficulty().getName(),
				e.getCategories().stream().map(this::catRef)
						.sorted(java.util.Comparator.comparing(QuestionCategoryRef::name)).toList(),
				e.getStatus(), e.getVersion(), mediaView(e.getPromptMedia()), e.getCodeLanguage(), e.getCodeContent(),
				new QuestionAnswerSettings(readAnswers(e.getAcceptedAnswersJson()), e.isCaseSensitive(),
						e.isManualReview(), e.getNumericMin(), e.getNumericMax(), e.getNumericTolerance(), e
								.getResponseMaxLength()),
				e.getOptions().stream().map(o -> new QuestionOptionView(o.getPublicId(), o.getOptionOrder(),
						o.getText(), mediaView(o.getMedia()), o.isCorrect())).toList(),
				e.getCreatedAt(), e.getUpdatedAt());
	}

	private QuestionSummary toSummary(QuestionJpaEntity e) {
		return new QuestionSummary(e.getPublicId(), e.getStatement(), e.getType().getCode(), e.getType().getName(),
				e.getDifficulty().getCode(), e.getDifficulty().getName(),
				e.getCategories().stream().map(this::catRef)
						.sorted(java.util.Comparator.comparing(QuestionCategoryRef::name)).toList(),
				e.getStatus(),
				e.getPromptMedia() != null || e.getOptions().stream().anyMatch(o -> o.getMedia() != null),
				e.getCreatedAt(), e.getUpdatedAt());
	}

	private QuestionCategoryRef catRef(QuestionCategoryJpaEntity c) {
		return new QuestionCategoryRef(c.getPublicId(), c.getCode(), c.getName(), c.getStatus());
	}

	private QuestionMediaView mediaView(QuestionMediaJpaEntity m) {
		return m == null ? null
				: new QuestionMediaView(m.getPublicId(), m.getOriginalName(), m.getContentType(), m.getSize(),
						"/api/v1/question-media/" + m.getPublicId());
	}

	private String norm(String v) {
		return v == null || v.isBlank() ? null : v.trim().toUpperCase(Locale.ROOT);
	}

	private String trim(String v) {
		return v == null ? null : v.trim();
	}

	private String nullable(String v) {
		return v == null || v.isBlank() ? null : v.trim();
	}

	private String code(String v) {
		return v == null || v.isBlank() ? null : v.trim().toUpperCase(Locale.ROOT);
	}

	private BusinessException error(String c, String m) {
		return new BusinessException(c, m);
	}

	private record Selection(QuestionTypeJpaEntity type, QuestionDifficultyJpaEntity difficulty,
			Set<QuestionCategoryJpaEntity> categories) {
	}
}
