package com.nexoskill.evaluation.questionbank.infrastructure.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexoskill.evaluation.globalcontent.application.service.ContentSynchronizationService;
import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.globalcontent.application.service.GlobalContentAccessPolicy;
import com.nexoskill.evaluation.globalcontent.domain.model.GlobalContentType;
import com.nexoskill.evaluation.organizations.application.TenantContextResolver;
import com.nexoskill.evaluation.organizations.domain.model.ContentScope;
import com.nexoskill.evaluation.organizations.domain.model.TenantContext;
import com.nexoskill.evaluation.questionbank.application.model.*;
import com.nexoskill.evaluation.questionbank.application.service.QuestionOperationContextPolicy;
import com.nexoskill.evaluation.questionbank.application.service.QuestionCreationTargetResolver;
import com.nexoskill.evaluation.questionbank.application.port.out.*;
import com.nexoskill.evaluation.questionbank.domain.model.*;
import com.nexoskill.evaluation.shared.domain.*;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class OracleQuestionBankAdapter implements QuestionBankPort {
	private static final String INTERNAL_DIFFICULTY = "JR";

	private final SpringDataQuestionRepository questions;
	private final SpringDataQuestionTypeRepository types;
	private final SpringDataQuestionDifficultyRepository difficulties;
	private final SpringDataQuestionTechnologyRepository technologies;
	private final SpringDataQuestionCategoryRepository categories;
	private final QuestionGovernanceSearchRepository governanceSearch;
	private final QuestionTagStore tagStore;
	private final SpringDataQuestionMediaRepository media;
	private final QuestionUsageChecker usage;
	private final NamedParameterJdbcTemplate jdbc;
	private final ObjectMapper json;
	private final Clock clock;
	private final TenantContextResolver tenantContextResolver;
	private final GlobalContentAccessPolicy accessPolicy;
	private final QuestionMediaAccessPolicy mediaAccessPolicy;
	private final QuestionOperationContextPolicy operationContextPolicy;
	private final QuestionCreationTargetResolver creationTargetResolver;
	private final ContentSynchronizationService synchronization;
	private final AuditLogPort audit;
	private final HttpServletRequest request;

	public OracleQuestionBankAdapter(SpringDataQuestionRepository questions, SpringDataQuestionTypeRepository types,
			SpringDataQuestionDifficultyRepository difficulties, SpringDataQuestionTechnologyRepository technologies,
			SpringDataQuestionCategoryRepository categories, QuestionGovernanceSearchRepository governanceSearch,
			QuestionTagStore tagStore, SpringDataQuestionMediaRepository media, QuestionUsageChecker usage,
			NamedParameterJdbcTemplate jdbc, ObjectMapper json, Clock clock,
			TenantContextResolver tenantContextResolver, GlobalContentAccessPolicy accessPolicy,
			QuestionMediaAccessPolicy mediaAccessPolicy, QuestionOperationContextPolicy operationContextPolicy,
			QuestionCreationTargetResolver creationTargetResolver, ContentSynchronizationService synchronization,
			AuditLogPort audit, HttpServletRequest request) {
		this.questions = questions;
		this.types = types;
		this.difficulties = difficulties;
		this.technologies = technologies;
		this.categories = categories;
		this.governanceSearch = governanceSearch;
		this.tagStore = tagStore;
		this.media = media;
		this.usage = usage;
		this.jdbc = jdbc;
		this.json = json;
		this.clock = clock;
		this.tenantContextResolver = tenantContextResolver;
		this.accessPolicy = accessPolicy;
		this.mediaAccessPolicy = mediaAccessPolicy;
		this.operationContextPolicy = operationContextPolicy;
		this.creationTargetResolver = creationTargetResolver;
		this.synchronization = synchronization;
		this.audit = audit;
		this.request = request;
	}

	@Override
	public QuestionDetail create(CreateQuestionCommand command) {
		var tenant = tenantContextResolver.resolve(request);
		var target = creationTargetResolver.resolve(tenant, command.contentScope(), command.organizationPublicId());
		var ownership = new GlobalContentAccessPolicy.Ownership(target.scope(), target.organizationId());
		var selection = selection(command.typeCode(), command.categoryPublicIds(), Set.of(), ownership.scope(),
				ownership.organizationId());
		var settings = command.answerSettings();
		var entity = QuestionJpaEntity.create(UUID.randomUUID().toString(), selection.type(),
				difficulty(command.difficultyCode()),
				technology(command.technologyPublicId(), ownership.scope(), ownership.organizationId(), null),
				command.levelCode(), selection.categories(), trim(command.statement()), nullable(command.explanation()),
				optionalMedia(command.promptMediaPublicId(), command.actorUserId(), ownership.scope(),
						ownership.organizationId()),
				javaLanguage(command.codeContent()), nullable(command.codeContent()),
				writeAnswers(settings.acceptedAnswers()), settings.caseSensitive(),
				selection.type().getCode().equals(QuestionTypeCode.OPEN_TEXT.name()), null, null, null,
				settings.maxLength(), command.actorUserId(), clock.instant());
		entity.assignOwnership(ownership.scope(), ownership.organizationId());
		addOptions(entity, command.options(), command.actorUserId(), ownership.scope(), ownership.organizationId());
		QuestionJpaEntity saved = questions.saveAndFlush(entity);
		tagStore.replace(saved.getId(), command.tags(), saved.getContentScope(), saved.getOwnerOrganizationId(),
				command.actorUserId(), clock.instant());
		auditQuestion(command.actorUserId(), "QUESTION_CREATED", saved, Map.of());
		return toDetail(saved, memberships(List.of(saved.getId())), governanceSearch.metadata(saved.getId()));
	}

	@Override
	public QuestionDetail update(UpdateQuestionCommand command) {

		var entity = locked(command.publicId());
		var tenant = tenantContextResolver.resolve(request);
		assertEditable(entity);
		checkVersion(entity, command.expectedEntityVersion());
		if (entity.getStatus() == QuestionStatus.ARCHIVED) {
			throw error("QUESTION_ARCHIVED", "Reactiva la pregunta antes de editarla.");
		}
		if (entity.getStatus() == QuestionStatus.DELETED) {
			throw error("QUESTION_DELETED", "Restaura la pregunta antes de editarla.");
		}
		boolean transversalEdit = tenant.globalAdministrator() && entity.getContentScope() == ContentScope.ORGANIZATION;
		String previousStatement = entity.getStatement();

		Set<String> existing = entity.getCategories().stream().map(QuestionCategoryJpaEntity::getPublicId)
				.collect(java.util.stream.Collectors.toSet());
		var selection = selection(command.typeCode(), command.categoryPublicIds(), existing, entity.getContentScope(),
				entity.getOwnerOrganizationId());
		var settings = command.answerSettings();
		entity.clearOptions();
		questions.flush();
		entity.apply(selection.type(), difficulty(command.difficultyCode()),
				technology(command.technologyPublicId(), entity.getContentScope(), entity.getOwnerOrganizationId(),
						entity.getTechnology() == null ? null : entity.getTechnology().getPublicId()),
				command.levelCode(), selection.categories(), trim(command.statement()), nullable(command.explanation()),
				optionalMedia(command.promptMediaPublicId(), command.actorUserId(), entity.getContentScope(),
						entity.getOwnerOrganizationId()),
				javaLanguage(command.codeContent()), nullable(command.codeContent()),
				writeAnswers(settings.acceptedAnswers()), settings.caseSensitive(),
				selection.type().getCode().equals(QuestionTypeCode.OPEN_TEXT.name()), null, null, null,
				settings.maxLength(), command.actorUserId(), clock.instant());
		markCustomized(entity);
		addOptions(entity, command.options(), command.actorUserId(), entity.getContentScope(),
				entity.getOwnerOrganizationId());
		QuestionJpaEntity saved = questions.saveAndFlush(entity);
		tagStore.replace(saved.getId(), command.tags(), saved.getContentScope(), saved.getOwnerOrganizationId(),
				command.actorUserId(), clock.instant());
		if (transversalEdit) {
			HashMap<String, Object> data = new HashMap<>();
			data.put("questionPublicId", saved.getPublicId());
			data.put("organizationId", saved.getOwnerOrganizationId());
			data.put("previousStatement", previousStatement);
			data.put("newStatement", saved.getStatement());
			audit.record(command.actorUserId(), "ORGANIZATIONAL_QUESTION_EDITED_BY_GLOBAL_ADMIN", "QUESTION_BANK",
					"El Administrador global modificó contenido propiedad de una organización.", null, null, data,
					clock.instant());
		}
		auditQuestion(command.actorUserId(), "QUESTION_UPDATED", saved, Map.of("previousStatement", previousStatement));
		return toDetail(saved, memberships(List.of(saved.getId())), governanceSearch.metadata(saved.getId()));
	}

	@Override
	public QuestionDetail get(String id) {
		QuestionJpaEntity entity = find(id);
		assertReadable(entity);
		return toDetail(entity, memberships(List.of(entity.getId())), governanceSearch.metadata(entity.getId()));
	}

	@Override
	public QuestionPage search(QuestionSearchFilter filter, int page, int size) {
		var tenant = tenantContextResolver.resolve(request);
		var result = governanceSearch.search(filter, tenant, page, size);
		List<Long> ids = result.rows().stream().map(QuestionGovernanceSearchRepository.SearchRow::questionId).toList();
		Map<Long, QuestionJpaEntity> byId = questions.findAllById(ids).stream()
				.collect(java.util.stream.Collectors.toMap(QuestionJpaEntity::getId, value -> value));
		Map<Long, QuestionGovernanceSearchRepository.SearchRow> metadata = result.rows().stream()
				.collect(java.util.stream.Collectors.toMap(QuestionGovernanceSearchRepository.SearchRow::questionId,
						value -> value));
		MembershipIndex membershipIndex = memberships(ids);
		Map<Long, List<QuestionTagView>> tagsByQuestion = tagStore.findByQuestionIds(ids);
		List<QuestionSummary> content = ids.stream().map(byId::get).filter(Objects::nonNull)
				.map(entity -> toSummary(entity, membershipIndex, metadata.get(entity.getId()),
						tagsByQuestion.getOrDefault(entity.getId(), List.of())))
				.toList();
		int totalPages = size == 0 ? 0 : (int) Math.ceil((double) result.totalElements() / size);
		return new QuestionPage(content, page, size, result.totalElements(), totalPages);
	}

	@Override
	public List<QuestionTagView> suggestTags(String query, int limit) {
		var tenant = tenantContextResolver.resolve(request);
		var ownership = accessPolicy.ownershipForCreation(tenant);
		return tagStore.suggest(ownership.scope(), ownership.organizationId(), query, limit);
	}

	@Override
	public QuestionDetail duplicate(String id, Long actor) {
		var source = lockedForDuplicate(id);
		assertReadable(source);
		assertEditable(source);
		validateDuplicable(source);
		return duplicateSource(source,
				new GlobalContentAccessPolicy.Ownership(source.getContentScope(), source.getOwnerOrganizationId()),
				actor);
	}

	@Override
	public QuestionDetail duplicateGlobalToOrganization(String id, String organizationPublicId, Long actor) {
		var tenant = tenantContextResolver.resolve(request);
		if (tenant == null || !tenant.globalAdministrator()) {
			throw error("QUESTION_DUPLICATE_TARGET_FORBIDDEN",
					"Solo el Administrador global puede seleccionar otro destino para la duplicación.");
		}
		var target = creationTargetResolver.resolve(tenant, ContentScope.ORGANIZATION.name(), organizationPublicId);
		var source = lockedForDuplicate(id);
		assertReadable(source);
		assertEditable(source);
		validateDuplicable(source);
		if (source.getContentScope() != ContentScope.GLOBAL) {
			throw error("QUESTION_DUPLICATE_GLOBAL_SOURCE_REQUIRED",
					"La selección de destino solo está disponible al duplicar una pregunta global.");
		}
		return duplicateSource(source,
				new GlobalContentAccessPolicy.Ownership(ContentScope.ORGANIZATION, target.organizationId()), actor);
	}

	private void validateDuplicable(QuestionJpaEntity source) {
		if (source.getStatus() == QuestionStatus.DELETED) {
			throw error("QUESTION_DELETED", "No se puede duplicar una pregunta eliminada.");
		}
		if (source.getCategories() == null || source.getCategories().isEmpty()) {
			throw error("QUESTION_DUPLICATE_CONFIGURATION_INVALID",
					"No fue posible duplicar la pregunta porque no tiene categorías válidas.");
		}
		if (source.getType() == null || source.getDifficulty() == null) {
			throw error("QUESTION_DUPLICATE_CONFIGURATION_INVALID",
					"No fue posible duplicar la pregunta porque su clasificación está incompleta.");
		}
	}

	private QuestionDetail duplicateSource(QuestionJpaEntity source, GlobalContentAccessPolicy.Ownership ownership,
			Long actor) {
		try {
			Instant now = clock.instant();
			var entity = QuestionJpaEntity.create(UUID.randomUUID().toString(), source.getType(),
					source.getDifficulty(), source.getTechnology(), source.getLevelCode(),
					new LinkedHashSet<>(source.getCategories()), source.getStatement(), source.getExplanation(),
					source.getPromptMedia(), javaLanguage(source.getCodeContent()), source.getCodeContent(),
					source.getAcceptedAnswersJson(), source.isCaseSensitive(), source.isManualReview(), null, null,
					null, source.getResponseMaxLength(), actor, now);
			entity.assignOwnership(ownership.scope(), ownership.organizationId());
			for (var option : source.getOptions()) {
				entity.addOption(QuestionOptionJpaEntity.create(entity, UUID.randomUUID().toString(),
						option.getOptionOrder(), option.getText(), option.getMedia(), option.getMatchText(),
						option.getMatchMedia(), option.isCorrect(), option.getFeedback(), now));
			}
			QuestionJpaEntity saved = questions.saveAndFlush(entity);
			tagStore.copy(source.getId(), saved.getId(), ownership.scope(), ownership.organizationId(), actor, now);
			if (ownership.scope() == ContentScope.GLOBAL) {
				copyAvailability(source, saved, actor);
			}
			HashMap<String, Object> auditData = new HashMap<>();
			auditData.put("sourceQuestionPublicId", source.getPublicId());
			if (ownership.organizationId() != null) {
				auditData.put("targetOrganizationId", ownership.organizationId());
			}
			auditQuestion(actor, "QUESTION_DUPLICATED", saved, auditData);
			return toDetail(saved, memberships(List.of(saved.getId())), governanceSearch.metadata(saved.getId()));
		} catch (DataIntegrityViolationException exception) {
			throw error("QUESTION_DUPLICATE_CONFIGURATION_INVALID",
					"No fue posible duplicar la pregunta porque contiene una configuración incompatible.");
		}
	}

	@Override
	public QuestionDetail copyToOrganization(String id, Long actor) {
		var tenant = tenantContextResolver.resolve(request);
		if (tenant.globalAdministrator() || !tenant.hasOrganization()) {
			throw error("QUESTION_ORGANIZATION_COPY_FORBIDDEN",
					"La copia organizacional debe crearse desde una organización autorizada.");
		}
		var source = lockedForDuplicate(id);
		assertReadable(source);
		return copyGlobalSourceToOrganization(source, tenant.organizationId(), actor);
	}

	@Override
	public QuestionDetail copyGlobalToOrganization(String id, String organizationPublicId, Long actor) {
		var tenant = tenantContextResolver.resolve(request);
		if (tenant == null || !tenant.globalAdministrator()) {
			throw error("QUESTION_DISTRIBUTION_FORBIDDEN",
					"Solo el Administrador global puede distribuir preguntas hacia organizaciones.");
		}
		var target = creationTargetResolver.resolve(tenant, ContentScope.ORGANIZATION.name(), organizationPublicId);
		var source = lockedForDuplicate(id);
		assertReadable(source);
		return copyGlobalSourceToOrganization(source, target.organizationId(), actor);
	}

	@Override
	public List<String> activeCommercialOrganizationPublicIds() {
		var tenant = tenantContextResolver.resolve(request);
		if (tenant == null || !tenant.globalAdministrator()) {
			throw error("QUESTION_DISTRIBUTION_FORBIDDEN",
					"Solo el Administrador global puede consultar destinos de distribución.");
		}
		return jdbc.queryForList("""
				SELECT PUBLIC_ID
				  FROM ORGANIZATION
				 WHERE ORGANIZATION_TYPE = 'CUSTOMER'
				   AND STATUS = 'ACTIVE'
				   AND VALID_FROM <= TRUNC(SYSDATE)
				   AND (EXPIRES_ON IS NULL OR EXPIRES_ON >= TRUNC(SYSDATE))
				 ORDER BY ORGANIZATION_NAME, ORGANIZATION_CODE
				""", Map.of(), String.class);
	}

	private QuestionDetail copyGlobalSourceToOrganization(QuestionJpaEntity source, Long targetOrganizationId,
			Long actor) {
		if (source.getContentScope() != ContentScope.GLOBAL) {
			throw error("QUESTION_GLOBAL_SOURCE_REQUIRED",
					"Solo una pregunta GLOBAL puede distribuirse a una organización.");
		}
		if (source.getStatus() != QuestionStatus.ACTIVE) {
			throw error("QUESTION_GLOBAL_SOURCE_INACTIVE",
					"La pregunta GLOBAL debe estar activa para crear una copia organizacional.");
		}
		if (targetOrganizationId == null) {
			throw error("QUESTION_OWNER_ORGANIZATION_REQUIRED", "Selecciona la organización propietaria de la copia.");
		}
		if (questions.existsBySourceGlobalIdAndOwnerOrganizationIdAndStatusNot(source.getId(), targetOrganizationId,
				QuestionStatus.DELETED)) {
			throw error("QUESTION_ORGANIZATION_COPY_EXISTS",
					"La organización ya cuenta con una copia de esta pregunta GLOBAL.");
		}

		Instant now = clock.instant();
		LinkedHashSet<QuestionCategoryJpaEntity> targetCategories = new LinkedHashSet<>();
		for (QuestionCategoryJpaEntity sourceCategory : source.getCategories()) {
			if (sourceCategory.getContentScope() != ContentScope.GLOBAL) {
				throw error("QUESTION_CATEGORY_SCOPE_INVALID",
						"Una categoría relacionada no pertenece al catálogo GLOBAL.");
			}
			QuestionCategoryJpaEntity targetCategory = categories
					.findFirstBySourceGlobalIdAndOwnerOrganizationId(sourceCategory.getId(), targetOrganizationId)
					.orElseGet(
							() -> categories
									.findFirstByContentScopeAndOwnerOrganizationIdAndCodeIgnoreCase(
											ContentScope.ORGANIZATION, targetOrganizationId, sourceCategory.getCode())
									.orElseGet(() -> {
										QuestionCategoryJpaEntity created = QuestionCategoryJpaEntity.create(
												UUID.randomUUID().toString(), sourceCategory.getCode(),
												sourceCategory.getName(), sourceCategory.getDescription(),
												ContentScope.ORGANIZATION, targetOrganizationId, actor, now);
										created.linkToGlobalSource(sourceCategory.getId(), sourceCategory.getVersion(),
												now);
										return categories.saveAndFlush(created);
									}));
			if (targetCategory.getStatus() != CatalogStatus.ACTIVE) {
				throw error("QUESTION_CATEGORY_INACTIVE",
						"Una categoría necesaria para la copia está inactiva en la organización.");
			}
			targetCategories.add(targetCategory);
		}

		var entity = QuestionJpaEntity.create(UUID.randomUUID().toString(), source.getType(), source.getDifficulty(),
				source.getTechnology(), source.getLevelCode(), targetCategories, source.getStatement(),
				source.getExplanation(), source.getPromptMedia(), javaLanguage(source.getCodeContent()),
				source.getCodeContent(), source.getAcceptedAnswersJson(), source.isCaseSensitive(),
				source.isManualReview(), null, null, null, source.getResponseMaxLength(), actor, now);
		entity.assignOwnership(ContentScope.ORGANIZATION, targetOrganizationId);
		entity.linkToGlobalSource(source.getId(), source.getVersion(), now);
		for (var option : source.getOptions()) {
			entity.addOption(QuestionOptionJpaEntity.create(entity, UUID.randomUUID().toString(),
					option.getOptionOrder(), option.getText(), option.getMedia(), option.getMatchText(),
					option.getMatchMedia(), option.isCorrect(), option.getFeedback(), now));
		}
		QuestionJpaEntity saved = questions.saveAndFlush(entity);
		tagStore.copy(source.getId(), saved.getId(), ContentScope.ORGANIZATION, targetOrganizationId, actor, now);
		auditQuestion(actor, "QUESTION_ORGANIZATION_COPY_CREATED", saved,
				Map.of("sourceGlobalQuestionPublicId", source.getPublicId()));
		return toDetail(saved, memberships(List.of(saved.getId())), governanceSearch.metadata(saved.getId()));
	}

	@Override
	public QuestionDetail changeStatus(String id, QuestionStatus status, long expected, Long actor) {

		var entity = locked(id);
		assertEditable(entity);
		checkVersion(entity, expected);
		if (entity.getStatus() == QuestionStatus.DELETED) {
			throw error("QUESTION_DELETED", "Restaura la pregunta antes de cambiar su estado.");
		}
		boolean activeDependencies = usage.isUsedByActiveExam(entity.getId());
		entity.changeStatus(status, actor, clock.instant());
		markCustomized(entity);
		QuestionJpaEntity saved = questions.saveAndFlush(entity);
		auditQuestion(actor, status == QuestionStatus.ACTIVE ? "QUESTION_ACTIVATED" : "QUESTION_INACTIVATED", saved,
				Map.of("activeDependenciesPreserved", activeDependencies));
		return toDetail(saved, memberships(List.of(saved.getId())), governanceSearch.metadata(saved.getId()));
	}

	@Override
	public QuestionDetail softDelete(String id, long expected, String reason, Long actor) {
		var entity = locked(id);
		assertEditable(entity);
		checkVersion(entity, expected);
		if (entity.getStatus() == QuestionStatus.DELETED) {
			throw error("QUESTION_ALREADY_DELETED", "La pregunta ya está eliminada.");
		}
		boolean activeDependencies = usage.isUsedByActiveExam(entity.getId());
		QuestionUsageChecker.DetachmentResult detachment = usage.detachFromForms(entity.getId());
		entity.softDelete(actor, clock.instant(), truncate(reason, 500));
		markCustomized(entity);
		QuestionJpaEntity saved = questions.saveAndFlush(entity);
		auditQuestion(actor, "QUESTION_LOGICALLY_DELETED", saved,
				Map.of("reason", reason == null ? "" : truncate(reason, 500), "hadActiveDependencies",
						activeDependencies, "removedFixedFormRelations", detachment.fixedFormRelations(),
						"removedCollectionRelations", detachment.collectionRelations()));
		return toDetail(saved, memberships(List.of(saved.getId())), governanceSearch.metadata(saved.getId()));
	}

	@Override
	public QuestionDetail restore(String id, long expected, Long actor) {
		var entity = locked(id);
		assertEditable(entity);
		checkVersion(entity, expected);
		if (entity.getStatus() != QuestionStatus.DELETED) {
			throw error("QUESTION_NOT_DELETED", "La pregunta no está eliminada.");
		}
		entity.restore(actor, clock.instant());
		markCustomized(entity);
		QuestionJpaEntity saved = questions.saveAndFlush(entity);
		auditQuestion(actor, "QUESTION_RESTORED", saved, Map.of());
		return toDetail(saved, memberships(List.of(saved.getId())), governanceSearch.metadata(saved.getId()));
	}

	private void copyAvailability(QuestionJpaEntity source, QuestionJpaEntity target, Long actor) {
		if (source.getContentScope() != ContentScope.GLOBAL)
			return;
		var now = clock.instant();
		// A duplicate is created unpublished to avoid exposing content accidentally.
		jdbc.update("""
				UPDATE QUESTION
				   SET AVAILABILITY_MODE = 'NONE', UPDATED_BY = :actor, UPDATED_AT = :now
				 WHERE QUESTION_ID = :targetQuestionId
				""", Map.of("targetQuestionId", target.getId(), "actor", actor, "now", now));
	}

	private void auditQuestion(Long actor, String event, QuestionJpaEntity entity, Map<String, Object> extra) {
		HashMap<String, Object> data = new HashMap<>(extra);
		data.put("questionPublicId", entity.getPublicId());
		data.put("scope", entity.getContentScope().name());
		if (entity.getOwnerOrganizationId() != null) {
			data.put("organizationId", entity.getOwnerOrganizationId());
		}
		audit.record(actor, event, "QUESTION_BANK", "Se ejecutó una operación administrativa sobre una pregunta.", null,
				null, data, clock.instant());
	}

	private void addOptions(QuestionJpaEntity entity, List<QuestionOptionCommand> commands, Long actorUserId,
			ContentScope targetScope, Long targetOrganizationId) {
		int order = 1;
		for (var command : commands) {
			entity.addOption(QuestionOptionJpaEntity.create(entity, UUID.randomUUID().toString(), order++,
					nullable(command.text()),
					optionalMedia(command.mediaPublicId(), actorUserId, targetScope, targetOrganizationId),
					nullable(command.matchText()),
					optionalMedia(command.matchMediaPublicId(), actorUserId, targetScope, targetOrganizationId),
					command.correct(), nullable(command.feedback()), clock.instant()));
		}
	}

	private Selection selection(String type, List<String> ids, Set<String> existing, ContentScope targetScope,
			Long targetOrganizationId) {
		var selectedType = types.findByCodeAndStatus(norm(type), CatalogStatus.ACTIVE)
				.orElseThrow(() -> error("QUESTION_TYPE_INVALID", "El tipo de pregunta no está disponible."));
		if (ids == null || ids.isEmpty()) {
			throw error("QUESTION_CATEGORY_REQUIRED", "Selecciona al menos una categoría.");
		}
		LinkedHashSet<QuestionCategoryJpaEntity> selectedCategories = new LinkedHashSet<>();
		for (String rawId : ids) {
			String id = PublicIdNormalizer.requiredUuid(rawId, "QUESTION_CATEGORY_INVALID",
					"Una categoría seleccionada no es válida.");
			var category = categories.findByPublicId(id)
					.orElseThrow(() -> error("CATEGORY_NOT_FOUND", "La categoría seleccionada no existe."));
			var tenant = tenantContextResolver.resolve(request);
			boolean compatible = targetScope == ContentScope.GLOBAL ? category.getContentScope() == ContentScope.GLOBAL
					: (category.getContentScope() == ContentScope.ORGANIZATION
							&& Objects.equals(category.getOwnerOrganizationId(), targetOrganizationId))
							|| (category.getContentScope() == ContentScope.GLOBAL
									&& ((tenant != null && tenant.globalAdministrator()) || existing.contains(id)));
			if (!compatible) {
				throw error("QUESTION_CATEGORY_SCOPE_MISMATCH",
						"La categoría no pertenece al alcance autorizado para esta pregunta.");
			}
			if (category.getStatus() == CatalogStatus.DELETED) {
				throw error("CATEGORY_NOT_FOUND", "La categoría seleccionada no existe.");
			}
			if (category.getStatus() != CatalogStatus.ACTIVE && !existing.contains(id)) {
				throw error("CATEGORY_INACTIVE",
						"La categoría se encuentra inactiva y no puede asignarse a nuevas preguntas.");
			}
			selectedCategories.add(category);
		}
		return new Selection(selectedType, selectedCategories);
	}

	private QuestionDifficultyJpaEntity difficulty(String code) {
		String normalized = norm(code);
		if (normalized == null)
			normalized = INTERNAL_DIFFICULTY;
		return difficulties.findByCodeAndStatus(normalized, CatalogStatus.ACTIVE).orElseThrow(
				() -> error("QUESTION_DIFFICULTY_INVALID", "La dificultad seleccionada no está disponible."));
	}

	private QuestionTechnologyJpaEntity technology(String publicId, ContentScope questionScope,
			Long questionOrganizationId, String retainedTechnologyPublicId) {
		if (publicId == null || publicId.isBlank())
			return null;
		String normalized = PublicIdNormalizer.requiredUuid(publicId, "QUESTION_TECHNOLOGY_INVALID",
				"La tecnología seleccionada no es válida.");
		QuestionTechnologyJpaEntity value = technologies.findByPublicId(normalized)
				.orElseThrow(() -> error("QUESTION_TECHNOLOGY_NOT_FOUND", "La tecnología seleccionada no existe."));
		boolean retainedTechnology = Objects.equals(value.getPublicId(), retainedTechnologyPublicId);
		if (value.getStatus() != QuestionTechnologyStatus.ACTIVE && !retainedTechnology) {
			throw error("QUESTION_TECHNOLOGY_INACTIVE", "La tecnología seleccionada no se encuentra activa.");
		}
		var tenant = tenantContextResolver.resolve(request);
		boolean compatible = questionScope == ContentScope.GLOBAL ? value.getContentScope() == ContentScope.GLOBAL
				: (value.getContentScope() == ContentScope.ORGANIZATION
						&& Objects.equals(value.getOwnerOrganizationId(), questionOrganizationId))
						|| (value.getContentScope() == ContentScope.GLOBAL
								&& ((tenant != null && tenant.globalAdministrator()) || retainedTechnology));
		if (!compatible) {
			throw error("QUESTION_TECHNOLOGY_SCOPE_MISMATCH",
					"La tecnología seleccionada no pertenece al contexto de la pregunta.");
		}
		return value;
	}

	private QuestionJpaEntity find(String id) {
		String normalized = PublicIdNormalizer.requiredUuid(id, "QUESTION_ID_INVALID",
				"La pregunta indicada no es válida.");
		return questions.findByPublicId(normalized)
				.orElseThrow(() -> error("QUESTION_NOT_FOUND", "La pregunta solicitada no existe."));
	}

	private QuestionJpaEntity locked(String id) {
		String normalized = PublicIdNormalizer.requiredUuid(id, "QUESTION_ID_INVALID",
				"La pregunta indicada no es válida.");
		return questions.findByPublicIdForUpdate(normalized)
				.orElseThrow(() -> error("QUESTION_NOT_FOUND", "La pregunta solicitada no existe."));
	}

	private QuestionJpaEntity lockedForDuplicate(String id) {
		String normalized = PublicIdNormalizer.requiredUuid(id, "QUESTION_ID_INVALID",
				"La pregunta indicada no es válida.");
		return questions.findByPublicIdForUpdate(normalized).orElseThrow(
				() -> error("QUESTION_DUPLICATE_SOURCE_NOT_FOUND", "La pregunta que intentas duplicar no existe."));
	}

	private void assertReadable(QuestionJpaEntity entity) {
		var tenant = tenantContextResolver.resolve(request);
		if (tenant.globalAdministrator())
			return;
		accessPolicy.assertReadable(GlobalContentType.QUESTION, entity.getId(), entity.getContentScope(),
				entity.getOwnerOrganizationId(), tenant, "QUESTION_ACCESS_FORBIDDEN",
				"No tienes permisos para acceder a esta pregunta.");
	}

	private void assertEditable(QuestionJpaEntity entity) {
		var tenant = tenantContextResolver.resolve(request);
		operationContextPolicy.assertCanManage(tenant, entity.getContentScope(), entity.getOwnerOrganizationId());
		if (!tenant.globalAdministrator() && entity.getSourceGlobalId() != null) {
			accessPolicy.assertEditable(GlobalContentType.QUESTION, entity.getId(), entity.getContentScope(),
					entity.getOwnerOrganizationId(), entity.getSourceGlobalId(), tenant);
		}
	}

	private void markCustomized(QuestionJpaEntity entity) {
		if (entity.getSourceGlobalId() == null)
			return;
		entity.markCustomized(clock.instant());
		synchronization.markCustomized(GlobalContentType.QUESTION, entity.getOwnerOrganizationId(), entity.getId());
	}

	private void checkVersion(QuestionJpaEntity entity, long expected) {
		if (entity.getVersion() != expected) {
			throw error("QUESTION_CONCURRENT_MODIFICATION",
					"La pregunta fue modificada por otra persona. Recarga la información antes de guardar.");
		}
	}

	private QuestionDetail toDetail(QuestionJpaEntity entity, MembershipIndex membershipIndex,
			QuestionGovernanceSearchRepository.SearchRow metadata) {
		return new QuestionDetail(entity.getPublicId(), entity.getStatement(), entity.getExplanation(),
				entity.getType().getCode(), entity.getType().getName(), entity.getDifficulty().getCode(),
				entity.getDifficulty().getName(), entity.getLevelCode(), technologyView(entity),
				entity.getCategories().stream().map(this::catRef)
						.sorted(Comparator.comparing(QuestionCategoryRef::name)).toList(),
				tagStore.findByQuestionId(entity.getId()), entity.getStatus(), entity.getVersion(),
				mediaView(entity.getPromptMedia()), entity.getCodeContent() == null ? null : "JAVA",
				entity.getCodeContent(),
				new QuestionAnswerSettings(readAnswers(entity.getAcceptedAnswersJson()), entity.isCaseSensitive(),
						entity.isManualReview(), null, null, null, entity.getResponseMaxLength()),
				entity.getOptions().stream()
						.map(option -> new QuestionOptionView(option.getPublicId(), option.getOptionOrder(),
								option.getText(), mediaView(option.getMedia()), option.getMatchText(),
								mediaView(option.getMatchMedia()), option.isCorrect(), option.getFeedback()))
						.toList(),
				ownership(entity, metadata), membershipIndex.forms(entity.getId()),
				membershipIndex.collections(entity.getId()), entity.getCreatedAt(), entity.getUpdatedAt());
	}

	private QuestionSummary toSummary(QuestionJpaEntity entity, MembershipIndex membershipIndex,
			QuestionGovernanceSearchRepository.SearchRow metadata, List<QuestionTagView> tags) {
		List<QuestionUsageRef> forms = membershipIndex.forms(entity.getId());
		List<QuestionUsageRef> collections = membershipIndex.collections(entity.getId());
		return new QuestionSummary(
				entity.getPublicId(), entity.getStatement(), entity.getType()
						.getCode(),
				entity.getType().getName(), entity.getDifficulty().getCode(), entity.getDifficulty().getName(),
				entity.getLevelCode(), technologyView(entity),
				entity.getCategories().stream().map(this::catRef)
						.sorted(Comparator.comparing(QuestionCategoryRef::name)).toList(),
				tags, entity.getStatus(),
				entity.getPromptMedia() != null || entity.getOptions().stream()
						.anyMatch(option -> option.getMedia() != null || option.getMatchMedia() != null),
				entity.getCodeContent() != null && !entity.getCodeContent().isBlank(),
				!forms.isEmpty() || !collections.isEmpty(), ownership(entity, metadata), forms, collections,
				entity.getVersion(), entity.getCreatedAt(), entity.getUpdatedAt());
	}

	private QuestionTechnologySummary technologyView(QuestionJpaEntity entity) {
		if (entity.getTechnology() == null)
			return null;
		var value = entity.getTechnology();
		return new QuestionTechnologySummary(value.getPublicId(), value.getCode(), value.getName(),
				value.getStatus().name(), value.getDisplayOrder());
	}

	private QuestionOwnershipView ownership(QuestionJpaEntity entity,
			QuestionGovernanceSearchRepository.SearchRow metadata) {
		QuestionGovernanceSearchRepository.SearchRow row = metadata == null ? governanceSearch.metadata(entity.getId())
				: metadata;
		return new QuestionOwnershipView(entity.getContentScope(), row.ownerOrganizationPublicId(),
				row.ownerOrganizationCode(), row.ownerOrganizationName(), row.creatorPublicId(), row.creatorName(),
				row.sourceOrganizationPublicId(), row.sourceOrganizationName(), row.sourceQuestionPublicId(),
				entity.getSourceOrganizationVersion(), entity.getSourceOrganizationQuestionId() != null);
	}

	private MembershipIndex memberships(List<Long> ids) {
		if (ids == null || ids.isEmpty())
			return MembershipIndex.empty();
		Map<Long, LinkedHashMap<String, QuestionUsageRef>> forms = new HashMap<>();
		Map<Long, LinkedHashMap<String, QuestionUsageRef>> collections = new HashMap<>();
		for (Object[] row : questions.findMemberships(ids)) {
			Long questionId = ((Number) row[0]).longValue();
			String formId = string(row[1]);
			String formTitle = string(row[2]);
			String collectionId = string(row[3]);
			String collectionName = string(row[4]);
			if (formId != null) {
				forms.computeIfAbsent(questionId, ignored -> new LinkedHashMap<>()).putIfAbsent(formId,
						new QuestionUsageRef(formId, formTitle));
			}
			if (collectionId != null) {
				collections.computeIfAbsent(questionId, ignored -> new LinkedHashMap<>()).putIfAbsent(collectionId,
						new QuestionUsageRef(collectionId, collectionName));
			}
		}
		return new MembershipIndex(forms, collections);
	}

	private String string(Object value) {
		return value == null ? null : value.toString();
	}

	private QuestionCategoryRef catRef(QuestionCategoryJpaEntity category) {
		return new QuestionCategoryRef(category.getPublicId(), category.getCode(), category.getName(),
				category.getStatus());
	}

	private QuestionMediaView mediaView(QuestionMediaJpaEntity value) {
		return value == null ? null
				: new QuestionMediaView(value.getPublicId(), value.getOriginalName(), value.getContentType(),
						value.getSize(), "/api/v1/question-media/" + value.getPublicId());
	}

	private String norm(String value) {
		return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
	}

	private String javaLanguage(String codeContent) {
		return codeContent == null || codeContent.isBlank() ? null : "JAVA";
	}

	private String trim(String value) {
		return value == null ? null : value.trim();
	}

	private String nullable(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private QuestionMediaJpaEntity optionalMedia(String id, Long actorUserId, ContentScope targetScope,
			Long targetOrganizationId) {
		if (id == null || id.isBlank())
			return null;
		String normalized = PublicIdNormalizer.requiredUuid(id, "QUESTION_MEDIA_INVALID",
				"La imagen indicada no es válida.");
		QuestionMediaJpaEntity value = media.findByPublicId(normalized)
				.orElseThrow(() -> error("QUESTION_MEDIA_NOT_FOUND", "La imagen indicada no existe."));
		TenantContext tenant = tenantContextResolver.resolve(request);
		mediaAccessPolicy.assertAssignable(value, actorUserId, tenant, targetScope, targetOrganizationId);
		return value;
	}

	private String writeAnswers(List<String> answers) {
		try {
			return json.writeValueAsString(answers == null ? List.of() : answers);
		} catch (Exception exception) {
			throw error("QUESTION_ANSWERS_INVALID", "No fue posible procesar la configuración de respuesta.");
		}
	}

	private List<String> readAnswers(String value) {
		if (value == null || value.isBlank())
			return List.of();
		try {
			return json.readValue(value, new TypeReference<List<String>>() {
			});
		} catch (Exception exception) {
			return List.of();
		}
	}

	private String truncate(String value, int max) {
		if (value == null)
			return null;
		String normalized = value.trim();
		return normalized.length() <= max ? normalized : normalized.substring(0, max);
	}

	private BusinessException error(String code, String message) {
		return new BusinessException(code, message);
	}

	private record Selection(QuestionTypeJpaEntity type, LinkedHashSet<QuestionCategoryJpaEntity> categories) {
	}

	private record MembershipIndex(Map<Long, LinkedHashMap<String, QuestionUsageRef>> formMap,
			Map<Long, LinkedHashMap<String, QuestionUsageRef>> collectionMap) {
		static MembershipIndex empty() {
			return new MembershipIndex(Map.of(), Map.of());
		}

		List<QuestionUsageRef> forms(Long id) {
			var values = formMap.get(id);
			return values == null ? List.of() : List.copyOf(values.values());
		}

		List<QuestionUsageRef> collections(Long id) {
			var values = collectionMap.get(id);
			return values == null ? List.of() : List.copyOf(values.values());
		}
	}
}
