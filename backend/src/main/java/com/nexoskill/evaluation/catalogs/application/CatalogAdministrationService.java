package com.nexoskill.evaluation.catalogs.application;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.catalogs.application.CatalogModels.Dependencies;
import com.nexoskill.evaluation.catalogs.application.CatalogModels.Item;
import com.nexoskill.evaluation.catalogs.application.CatalogModels.TypeSummary;
import com.nexoskill.evaluation.catalogs.application.CatalogModels.Upsert;
import com.nexoskill.evaluation.catalogs.domain.CatalogType;
import com.nexoskill.evaluation.certifications.infrastructure.persistence.CertificationTechnologyJpaEntity;
import com.nexoskill.evaluation.certifications.infrastructure.persistence.CertificationTechnologyRepository;
import com.nexoskill.evaluation.certifications.infrastructure.persistence.ProfessionalCertificationProfileJpaEntity;
import com.nexoskill.evaluation.certifications.infrastructure.persistence.ProfessionalCertificationProfileRepository;
import com.nexoskill.evaluation.certifications.infrastructure.persistence.TechnologicalProfileCatalogJpaEntity;
import com.nexoskill.evaluation.certifications.infrastructure.persistence.TechnologicalProfileCatalogRepository;
import com.nexoskill.evaluation.organizations.domain.model.ContentScope;
import com.nexoskill.evaluation.organizations.domain.model.OrganizationType;
import com.nexoskill.evaluation.organizations.domain.model.TenantContext;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationJpaEntity;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationRepository;
import com.nexoskill.evaluation.questionbank.domain.model.CatalogStatus;
import com.nexoskill.evaluation.questionbank.domain.model.QuestionTechnologyStatus;
import com.nexoskill.evaluation.questionbank.infrastructure.persistence.QuestionCategoryJpaEntity;
import com.nexoskill.evaluation.questionbank.infrastructure.persistence.QuestionCategoryStatusHistoryJpaEntity;
import com.nexoskill.evaluation.questionbank.infrastructure.persistence.QuestionCategoryStatusHistoryRepository;
import com.nexoskill.evaluation.questionbank.infrastructure.persistence.QuestionDifficultyJpaEntity;
import com.nexoskill.evaluation.questionbank.infrastructure.persistence.QuestionTechnologyJpaEntity;
import com.nexoskill.evaluation.questionbank.infrastructure.persistence.QuestionTypeJpaEntity;
import com.nexoskill.evaluation.questionbank.infrastructure.persistence.SpringDataQuestionCategoryRepository;
import com.nexoskill.evaluation.questionbank.infrastructure.persistence.SpringDataQuestionDifficultyRepository;
import com.nexoskill.evaluation.questionbank.infrastructure.persistence.SpringDataQuestionTechnologyRepository;
import com.nexoskill.evaluation.questionbank.infrastructure.persistence.SpringDataQuestionTypeRepository;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogAdministrationService {
	private static final String ACTIVE = "ACTIVE";
	private static final String INACTIVE = "INACTIVE";
	private static final Set<CatalogType> DIRECT_LIFECYCLE_TYPES = Set.of(CatalogType.CATEGORIES,
			CatalogType.TECHNOLOGIES, CatalogType.PROFESSIONAL_PROFILES, CatalogType.TECHNOLOGICAL_PROFILES);

	private final SpringDataQuestionCategoryRepository categories;
	private final QuestionCategoryStatusHistoryRepository categoryHistory;
	private final SpringDataQuestionTechnologyRepository technologies;
	private final SpringDataQuestionTypeRepository types;
	private final SpringDataQuestionDifficultyRepository difficulties;
	private final ProfessionalCertificationProfileRepository profiles;
	private final CertificationTechnologyRepository certificationTechnologies;
	private final TechnologicalProfileCatalogRepository technologicalProfiles;
	private final OrganizationRepository organizations;
	private final NamedParameterJdbcTemplate jdbc;
	private final AuditLogPort audit;
	private final Clock clock;

	public CatalogAdministrationService(SpringDataQuestionCategoryRepository categories,
			QuestionCategoryStatusHistoryRepository categoryHistory,
			SpringDataQuestionTechnologyRepository technologies, SpringDataQuestionTypeRepository types,
			SpringDataQuestionDifficultyRepository difficulties, ProfessionalCertificationProfileRepository profiles,
			CertificationTechnologyRepository certificationTechnologies,
			TechnologicalProfileCatalogRepository technologicalProfiles, OrganizationRepository organizations,
			NamedParameterJdbcTemplate jdbc, AuditLogPort audit, Clock clock) {
		this.categories = categories;
		this.categoryHistory = categoryHistory;
		this.technologies = technologies;
		this.types = types;
		this.difficulties = difficulties;
		this.profiles = profiles;
		this.certificationTechnologies = certificationTechnologies;
		this.technologicalProfiles = technologicalProfiles;
		this.organizations = organizations;
		this.jdbc = jdbc;
		this.audit = audit;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<TypeSummary> types(TenantContext tenant) {
		requireTenant(tenant);
		return Arrays.stream(CatalogType.values()).map(type -> typeSummary(type, tenant)).toList();
	}

	@Transactional(readOnly = true)
	public List<Item> items(CatalogType type, String status, String organizationPublicId, TenantContext tenant) {
		requireTenant(tenant);
		String normalizedStatus = parseStatus(status);
		if (!type.tenantAware() && !tenant.globalAdministrator()) {
			return globalItems(type, normalizedStatus);
		}
		if (tenant.globalAdministrator()) {
			return filterByOrganization(allItems(type, normalizedStatus), organizationPublicId);
		}
		return organizationVisibleItems(type, normalizedStatus, tenant.organizationId());
	}

	@Transactional(readOnly = true)
	public Item get(CatalogType type, String id, TenantContext tenant) {
		requireTenant(tenant);
		Item item = getWithoutSecurity(type, id);
		assertReadable(type, item, tenant);
		return item;
	}

	@Transactional
	public Item create(CatalogType type, Upsert request, Long actorId, TenantContext tenant) {
		requireTenant(tenant);
		validate(request);
		OwnershipTarget owner = resolveCreationOwner(type, request.organizationPublicId(), tenant);
		String code = normalizeCode(request.code());
		String name = clean(request.name(), 200);
		assertUnique(type, null, code, name, owner.scope(), owner.organizationPublicId());
		Instant now = clock.instant();
		Item result = switch (type) {
		case CATEGORIES ->
			categoryItem(categories.saveAndFlush(QuestionCategoryJpaEntity.create(UUID.randomUUID().toString(), code,
					name, cleanOptional(request.description(), 500), owner.scope(), categoryOwnerId(owner), actorId,
					now)));
		case TECHNOLOGIES -> {
			QuestionTechnologyJpaEntity entity = technologies.saveAndFlush(QuestionTechnologyJpaEntity.create(
					UUID.randomUUID().toString(), code, name, cleanOptional(request.description(), 500),
					order(request.displayOrder()), owner.scope(), owner.ownerOrganizationId(), actorId, now));
			synchronizeCertificationTechnology(entity, now);
			yield technologyItem(entity);
		}
		case PROFESSIONAL_PROFILES -> profileItem(
				profiles.saveAndFlush(ProfessionalCertificationProfileJpaEntity.create(UUID.randomUUID().toString(),
						code, name, cleanOptional(request.description(), 500), order(request.displayOrder()),
						parseSuggestedProfile(request.suggestedTechnologicalProfile(), owner), owner.scope(),
						owner.ownerOrganizationId(), actorId, now)));
		case TECHNOLOGICAL_PROFILES ->
			technologicalProfileItem(technologicalProfiles.saveAndFlush(TechnologicalProfileCatalogJpaEntity.create(
					UUID.randomUUID().toString(), code, name, cleanOptional(request.description(), 500),
					order(request.displayOrder()), owner.scope(), owner.ownerOrganizationId(), actorId, now)));
		case QUESTION_TYPES -> typeItem(types.saveAndFlush(QuestionTypeJpaEntity.create(code, name,
				cleanOptional(request.description(), 500), order(request.displayOrder()), actorId, now)));
		case DIFFICULTIES -> difficultyItem(difficulties.saveAndFlush(QuestionDifficultyJpaEntity.create(code, name,
				cleanOptional(request.description(), 500), order(request.displayOrder()), actorId, now)));
		};
		record(actorId, "CATALOG_ITEM_CREATED", type, result, "Se creó un valor de catálogo.", tenant);
		return result;
	}

	@Transactional
	public Item update(CatalogType type, String id, Upsert request, Long actorId, TenantContext tenant) {
		requireTenant(tenant);
		validate(request);
		Item current = getWithoutSecurity(type, id);
		assertWritable(type, current, tenant);
		String name = clean(request.name(), 200);
		if (request.code() != null && !normalizeCode(request.code()).equals(current.code())) {
			throw new BusinessException("CATALOG_CODE_IMMUTABLE",
					"El código es estable y no puede modificarse después de crear el registro.");
		}
		assertUnique(type, id, current.code(), name, ContentScope.valueOf(current.scope()),
				current.organizationPublicId());
		Instant now = clock.instant();
		Item result = switch (type) {
		case CATEGORIES -> {
			QuestionCategoryJpaEntity entity = requireCategory(id);
			checkVersion(entity.getVersion(), request.expectedVersion());
			entity.update(entity.getCode(), name, cleanOptional(request.description(), 500), actorId, now);
			yield categoryItem(categories.saveAndFlush(entity));
		}
		case TECHNOLOGIES -> {
			QuestionTechnologyJpaEntity entity = requireTechnology(id);
			checkVersion(entity.getVersion(), request.expectedVersion());
			entity.update(name, cleanOptional(request.description(), 500), order(request.displayOrder()), actorId, now);
			entity = technologies.saveAndFlush(entity);
			synchronizeCertificationTechnology(entity, now);
			yield technologyItem(entity);
		}
		case PROFESSIONAL_PROFILES -> {
			ProfessionalCertificationProfileJpaEntity entity = requireProfile(id);
			checkVersion(entity.getVersion(), request.expectedVersion());
			OwnershipTarget owner = new OwnershipTarget(entity.getContentScope(), entity.getOwnerOrganizationId(),
					current.organizationPublicId());
			entity.update(name, cleanOptional(request.description(), 500), order(request.displayOrder()),
					parseSuggestedProfile(request.suggestedTechnologicalProfile(), owner), actorId, now);
			yield profileItem(profiles.saveAndFlush(entity));
		}
		case TECHNOLOGICAL_PROFILES -> {
			TechnologicalProfileCatalogJpaEntity entity = requireTechnologicalProfile(id);
			checkVersion(entity.getVersion(), request.expectedVersion());
			entity.update(name, cleanOptional(request.description(), 500), order(request.displayOrder()), actorId, now);
			yield technologicalProfileItem(technologicalProfiles.saveAndFlush(entity));
		}
		case QUESTION_TYPES -> {
			QuestionTypeJpaEntity entity = requireType(id);
			checkVersion(entity.getVersion(), request.expectedVersion());
			entity.update(name, cleanOptional(request.description(), 500), order(request.displayOrder()), actorId, now);
			yield typeItem(types.saveAndFlush(entity));
		}
		case DIFFICULTIES -> {
			QuestionDifficultyJpaEntity entity = requireDifficulty(id);
			checkVersion(entity.getVersion(), request.expectedVersion());
			entity.update(name, cleanOptional(request.description(), 500), order(request.displayOrder()), actorId, now);
			yield difficultyItem(difficulties.saveAndFlush(entity));
		}
		};
		record(actorId, "CATALOG_ITEM_UPDATED", type, result, "Se actualizó un valor de catálogo.", tenant);
		return result;
	}

	@Transactional
	public Item changeStatus(CatalogType type, String id, String nextStatus, Long expectedVersion, Long actorId,
			TenantContext tenant) {
		requireTenant(tenant);
		Item current = getWithoutSecurity(type, id);
		assertWritable(type, current, tenant);
		String target = requireOperationalStatus(nextStatus);
		Instant now = clock.instant();
		Item result = switch (type) {
		case CATEGORIES -> {
			QuestionCategoryJpaEntity entity = requireCategory(id);
			checkVersion(entity.getVersion(), expectedVersion);
			if (target.equals(ACTIVE))
				entity.activate(actorId, now);
			else
				entity.deactivate(actorId, now);
			yield categoryItem(categories.saveAndFlush(entity));
		}
		case TECHNOLOGIES -> {
			QuestionTechnologyJpaEntity entity = requireTechnology(id);
			checkVersion(entity.getVersion(), expectedVersion);
			entity.changeStatus(QuestionTechnologyStatus.valueOf(target), actorId, now);
			entity = technologies.saveAndFlush(entity);
			synchronizeCertificationTechnology(entity, now);
			yield technologyItem(entity);
		}
		case PROFESSIONAL_PROFILES -> {
			ProfessionalCertificationProfileJpaEntity entity = requireProfile(id);
			checkVersion(entity.getVersion(), expectedVersion);
			entity.changeStatus(target, actorId, now);
			yield profileItem(profiles.saveAndFlush(entity));
		}
		case TECHNOLOGICAL_PROFILES -> {
			TechnologicalProfileCatalogJpaEntity entity = requireTechnologicalProfile(id);
			checkVersion(entity.getVersion(), expectedVersion);
			entity.changeStatus(target, actorId, now);
			yield technologicalProfileItem(technologicalProfiles.saveAndFlush(entity));
		}
		case QUESTION_TYPES -> {
			QuestionTypeJpaEntity entity = requireType(id);
			checkVersion(entity.getVersion(), expectedVersion);
			entity.changeStatus(CatalogStatus.valueOf(target), actorId, now);
			yield typeItem(types.saveAndFlush(entity));
		}
		case DIFFICULTIES -> {
			QuestionDifficultyJpaEntity entity = requireDifficulty(id);
			checkVersion(entity.getVersion(), expectedVersion);
			entity.changeStatus(CatalogStatus.valueOf(target), actorId, now);
			yield difficultyItem(difficulties.saveAndFlush(entity));
		}
		};
		record(actorId, target.equals(ACTIVE) ? "CATALOG_ITEM_ACTIVATED" : "CATALOG_ITEM_DEACTIVATED", type, result,
				"Se cambió el estado del valor de catálogo.", tenant);
		return result;
	}

	@Transactional(readOnly = true)
	public Dependencies dependencies(CatalogType type, String id, TenantContext tenant) {
		Item item = get(type, id, tenant);
		long count = dependencyCount(type, dependencyKey(type, id));
		List<String> details = count == 0 ? List.of() : List.of(dependencyLabel(type, count));
		boolean deletable = DIRECT_LIFECYCLE_TYPES.contains(type) ? count == 0
				: INACTIVE.equals(item.status()) && count == 0;
		return new Dependencies(count, details, deletable);
	}

	@Transactional
	public void delete(CatalogType type, String id, Long expectedVersion, Long actorId, TenantContext tenant) {
		requireTenant(tenant);
		Item item = getWithoutSecurity(type, id);
		assertWritable(type, item, tenant);
		boolean directLifecycle = DIRECT_LIFECYCLE_TYPES.contains(type);
		if (!directLifecycle && !INACTIVE.equals(item.status())) {
			throw new BusinessException("CATALOG_MUST_BE_INACTIVE",
					"Primero debes inactivar el registro antes de eliminarlo.");
		}
		checkVersion(item.version(), expectedVersion);
		long count = dependencyCount(type, dependencyKey(type, id));
		if (count > 0) {
			if (directLifecycle)
				throw catalogInUse(count);
			throw new BusinessException("CATALOG_IN_USE",
					"No es posible eliminar este registro porque está siendo utilizado por información existente. "
							+ "Puedes mantenerlo inactivo para impedir su uso en nuevos registros.");
		}
		try {
			switch (type) {
			case CATEGORIES -> {
				QuestionCategoryJpaEntity entity = requireCategory(id);
				categoryHistory.deleteAllByCategoryId(entity.getId());
				categoryHistory.flush();
				categories.delete(entity);
				categories.flush();
			}
			case TECHNOLOGIES -> {
				QuestionTechnologyJpaEntity entity = requireTechnology(id);
				certificationTechnologies.findByMasterTechnologyId(entity.getId())
						.ifPresent(certificationTechnologies::delete);
				certificationTechnologies.flush();
				technologies.delete(entity);
				technologies.flush();
			}
			case PROFESSIONAL_PROFILES -> {
				profiles.delete(requireProfile(id));
				profiles.flush();
			}
			case TECHNOLOGICAL_PROFILES -> {
				technologicalProfiles.delete(requireTechnologicalProfile(id));
				technologicalProfiles.flush();
			}
			case QUESTION_TYPES -> {
				types.delete(requireType(id));
				types.flush();
			}
			case DIFFICULTIES -> {
				difficulties.delete(requireDifficulty(id));
				difficulties.flush();
			}
			}
		} catch (DataIntegrityViolationException exception) {
			if (directLifecycle)
				throw catalogInUse();
			throw new BusinessException("CATALOG_IN_USE",
					"No es posible eliminar este registro porque está siendo utilizado por información existente. "
							+ "Puedes mantenerlo inactivo para impedir su uso en nuevos registros.");
		}
		record(actorId, "CATALOG_ITEM_DELETED", type, item,
				directLifecycle ? "Se eliminó físicamente un valor de catálogo sin dependencias."
						: "Se eliminó físicamente un valor inactivo sin dependencias.",
				tenant);
	}

	private TypeSummary typeSummary(CatalogType type, TenantContext tenant) {
		List<Item> values = tenant.globalAdministrator() ? allItems(type, null)
				: organizationVisibleItems(type, null, tenant.organizationId());
		return new TypeSummary(type, type.label(), type.description(),
				values.stream().filter(value -> ACTIVE.equals(value.status())).count(),
				values.stream().filter(value -> INACTIVE.equals(value.status())).count(), values.stream()
						.map(Item::updatedAt).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null),
				type.tenantAware());
	}

	private List<Item> organizationVisibleItems(CatalogType type, String status, Long organizationId) {
		return switch (type) {
		case CATEGORIES ->
			categories.findVisible(organizationId, status == null ? null : CatalogStatus.valueOf(status)).stream()
					.filter(value -> value.getStatus() != CatalogStatus.DELETED).map(this::categoryListItem).toList();
		case TECHNOLOGIES ->
			technologies.findVisible(organizationId, status == null ? null : QuestionTechnologyStatus.valueOf(status))
					.stream().map(this::technologyListItem).toList();
		case PROFESSIONAL_PROFILES ->
			profiles.findVisible(organizationId, status).stream().map(this::profileListItem).toList();
		case TECHNOLOGICAL_PROFILES -> technologicalProfiles.findVisible(organizationId, status).stream()
				.map(this::technologicalProfileListItem).toList();
		case QUESTION_TYPES, DIFFICULTIES -> globalItems(type, status);
		};
	}

	private List<Item> globalItems(CatalogType type, String status) {
		return allItems(type, status).stream().filter(value -> "GLOBAL".equals(value.scope())).toList();
	}

	private List<Item> allItems(CatalogType type, String status) {
		return switch (type) {
		case CATEGORIES -> categories.findAll().stream().filter(value -> value.getStatus() != CatalogStatus.DELETED)
				.filter(value -> matchesStatus(value.getStatus().name(), status))
				.sorted(Comparator.comparing(QuestionCategoryJpaEntity::getName)).map(this::categoryListItem).toList();
		case TECHNOLOGIES -> technologies.findAllByOrderByDisplayOrderAscNameAsc().stream()
				.filter(value -> matchesStatus(value.getStatus().name(), status)).map(this::technologyListItem)
				.toList();
		case PROFESSIONAL_PROFILES -> profiles.findAllByOrderBySortOrderAscNameAsc().stream()
				.filter(value -> matchesStatus(value.getStatus(), status)).map(this::profileListItem).toList();
		case TECHNOLOGICAL_PROFILES ->
			technologicalProfiles.findAll().stream().filter(value -> matchesStatus(value.getStatus(), status))
					.sorted(Comparator.comparingInt(TechnologicalProfileCatalogJpaEntity::getDisplayOrder)
							.thenComparing(TechnologicalProfileCatalogJpaEntity::getName))
					.map(this::technologicalProfileListItem).toList();
		case QUESTION_TYPES -> types.findAllByOrderByDisplayOrderAscNameAsc().stream()
				.filter(value -> value.getStatus() != CatalogStatus.DELETED)
				.filter(value -> matchesStatus(value.getStatus().name(), status)).map(this::typeListItem).toList();
		case DIFFICULTIES -> difficulties.findAllByOrderBySortOrderAsc().stream()
				.filter(value -> value.getStatus() != CatalogStatus.DELETED)
				.filter(value -> matchesStatus(value.getStatus().name(), status)).map(this::difficultyListItem)
				.toList();
		};
	}

	private List<Item> filterByOrganization(List<Item> values, String organizationPublicId) {
		if (organizationPublicId == null || organizationPublicId.isBlank())
			return values;
		OrganizationJpaEntity organization = organizations.findByPublicId(organizationPublicId.trim()).orElseThrow(
				() -> new BusinessException("ORGANIZATION_NOT_FOUND", "La organización solicitada no existe."));
		if (organization.isGlobal()) {
			return values.stream().filter(value -> "GLOBAL".equals(value.scope())).toList();
		}
		return values.stream().filter(value -> Objects.equals(organization.getPublicId(), value.organizationPublicId()))
				.toList();
	}

	private Item categoryItem(QuestionCategoryJpaEntity value) {
		return categoryItem(value, true);
	}

	private Item categoryListItem(QuestionCategoryJpaEntity value) {
		return categoryItem(value, false);
	}

	private Item categoryItem(QuestionCategoryJpaEntity value, boolean includeDependencies) {
		OrganizationJpaEntity owner = organizations.findById(value.getOwnerOrganizationId()).orElse(null);
		return new Item(value.getPublicId(), value.getCode(), value.getName(), value.getDescription(),
				value.getStatus().name(), 0, value.getContentScope().name(), owner == null ? null : owner.getPublicId(),
				owner == null ? scopeName(value.getContentScope()) : owner.getName(), null, value.getCreatedBy(),
				value.getUpdatedBy(), value.getCreatedAt(), value.getUpdatedAt(), value.getVersion(),
				includeDependencies ? dependencyCount(CatalogType.CATEGORIES, value.getId()) : 0);
	}

	private Item technologyItem(QuestionTechnologyJpaEntity value) {
		return technologyItem(value, true);
	}

	private Item technologyListItem(QuestionTechnologyJpaEntity value) {
		return technologyItem(value, true);
	}

	private Item technologyItem(QuestionTechnologyJpaEntity value, boolean includeDependencies) {
		OrganizationJpaEntity owner = organization(value.getOwnerOrganizationId());
		return new Item(value.getPublicId(), value.getCode(), value.getName(), value.getDescription(),
				value.getStatus().name(), value.getDisplayOrder(), value.getContentScope().name(),
				owner == null ? null : owner.getPublicId(),
				owner == null ? scopeName(value.getContentScope()) : owner.getName(), null, value.getCreatedBy(),
				value.getUpdatedBy(), value.getCreatedAt(), value.getUpdatedAt(), value.getVersion(),
				includeDependencies ? dependencyCount(CatalogType.TECHNOLOGIES, value.getId()) : 0);
	}

	private Item profileItem(ProfessionalCertificationProfileJpaEntity value) {
		return profileItem(value, true);
	}

	private Item profileListItem(ProfessionalCertificationProfileJpaEntity value) {
		return profileItem(value, true);
	}

	private Item profileItem(ProfessionalCertificationProfileJpaEntity value, boolean includeDependencies) {
		OrganizationJpaEntity owner = organization(value.getOwnerOrganizationId());
		return new Item(value.getPublicId(), value.getCode(), value.getName(), value.getDescription(),
				value.getStatus(), value.getSortOrder(), value.getContentScope().name(),
				owner == null ? null : owner.getPublicId(),
				owner == null ? scopeName(value.getContentScope()) : owner.getName(),
				value.getSuggestedTechnologicalProfile(), value.getCreatedBy(), value.getUpdatedBy(),
				value.getCreatedAt(), value.getUpdatedAt(), value.getVersion(),
				includeDependencies ? dependencyCount(CatalogType.PROFESSIONAL_PROFILES, value.getId()) : 0);
	}

	private Item technologicalProfileItem(TechnologicalProfileCatalogJpaEntity value) {
		return technologicalProfileItem(value, true);
	}

	private Item technologicalProfileListItem(TechnologicalProfileCatalogJpaEntity value) {
		return technologicalProfileItem(value, true);
	}

	private Item technologicalProfileItem(TechnologicalProfileCatalogJpaEntity value, boolean includeDependencies) {
		OrganizationJpaEntity owner = organization(value.getOwnerOrganizationId());
		return new Item(value.getPublicId(), value.getCode(), value.getName(), value.getDescription(),
				value.getStatus(), value.getDisplayOrder(), value.getContentScope().name(),
				owner == null ? null : owner.getPublicId(),
				owner == null ? scopeName(value.getContentScope()) : owner.getName(), null, value.getCreatedBy(),
				value.getUpdatedBy(), value.getCreatedAt(), value.getUpdatedAt(), value.getVersion(),
				includeDependencies ? dependencyCount(CatalogType.TECHNOLOGICAL_PROFILES, value.getId()) : 0);
	}

	private Item typeItem(QuestionTypeJpaEntity value) {
		return typeItem(value, true);
	}

	private Item typeListItem(QuestionTypeJpaEntity value) {
		return typeItem(value, false);
	}

	private Item typeItem(QuestionTypeJpaEntity value, boolean includeDependencies) {
		return new Item(value.getCode(), value.getCode(), value.getName(), value.getDescription(),
				value.getStatus().name(), value.getDisplayOrder(), "GLOBAL", null, "GLOBAL", null, value.getCreatedBy(),
				value.getUpdatedBy(), value.getCreatedAt(), value.getUpdatedAt(), value.getVersion(),
				includeDependencies ? dependencyCount(CatalogType.QUESTION_TYPES, value.getCode()) : 0);
	}

	private Item difficultyItem(QuestionDifficultyJpaEntity value) {
		return difficultyItem(value, true);
	}

	private Item difficultyListItem(QuestionDifficultyJpaEntity value) {
		return difficultyItem(value, false);
	}

	private Item difficultyItem(QuestionDifficultyJpaEntity value, boolean includeDependencies) {
		return new Item(value.getCode(), value.getCode(), value.getName(), value.getDescription(),
				value.getStatus().name(), value.getSortOrder(), "GLOBAL", null, "GLOBAL", null, value.getCreatedBy(),
				value.getUpdatedBy(), value.getCreatedAt(), value.getUpdatedAt(), value.getVersion(),
				includeDependencies ? dependencyCount(CatalogType.DIFFICULTIES, value.getCode()) : 0);
	}

	private OwnershipTarget resolveCreationOwner(CatalogType type, String requestedOrganizationPublicId,
			TenantContext tenant) {
		if (!type.tenantAware()) {
			requireGlobalScopeAdministrator(tenant);
			return new OwnershipTarget(ContentScope.GLOBAL, null, null);
		}
		if (!tenant.globalAdministrator()) {
			if (!tenant.hasOrganization())
				throw new AccessDeniedException("No se pudo resolver la organización autenticada.");
			if (requestedOrganizationPublicId != null && !requestedOrganizationPublicId.isBlank()
					&& !requestedOrganizationPublicId.equals(tenant.organizationPublicId())) {
				throw new AccessDeniedException("No puedes seleccionar una organización distinta a la de tu sesión.");
			}
			OrganizationJpaEntity organization = requireOperationalOrganization(tenant.organizationPublicId());
			return new OwnershipTarget(ContentScope.ORGANIZATION, organization.getId(), organization.getPublicId());
		}
		if (requestedOrganizationPublicId != null && !requestedOrganizationPublicId.isBlank()) {
			OrganizationJpaEntity organization = requireOperationalOrganization(requestedOrganizationPublicId);
			return new OwnershipTarget(ContentScope.ORGANIZATION, organization.getId(), organization.getPublicId());
		}
		if (!tenant.globalScope() && tenant.hasOrganization()) {
			OrganizationJpaEntity organization = requireOperationalOrganization(tenant.organizationPublicId());
			return new OwnershipTarget(ContentScope.ORGANIZATION, organization.getId(), organization.getPublicId());
		}
		return new OwnershipTarget(ContentScope.GLOBAL, null, null);
	}

	private OrganizationJpaEntity requireOperationalOrganization(String publicId) {
		OrganizationJpaEntity organization = organizations.findByPublicId(publicId).orElseThrow(
				() -> new BusinessException("ORGANIZATION_NOT_FOUND", "La organización solicitada no existe."));
		if (organization.getOrganizationType() != OrganizationType.CUSTOMER
				|| !organization.isOperational(LocalDate.now(clock))) {
			throw new BusinessException("ORGANIZATION_NOT_OPERATIONAL",
					"La organización propietaria debe ser comercial y estar activa.");
		}
		return organization;
	}

	private Long categoryOwnerId(OwnershipTarget owner) {
		if (owner.scope() == ContentScope.ORGANIZATION)
			return owner.ownerOrganizationId();
		return organizations.findByCode(OrganizationJpaEntity.GLOBAL_CODE)
				.orElseThrow(() -> new BusinessException("GLOBAL_ORGANIZATION_NOT_FOUND",
						"La organización GLOBAL no está configurada."))
				.getId();
	}

	private void assertReadable(CatalogType type, Item item, TenantContext tenant) {
		if (tenant.globalAdministrator())
			return;
		if (!type.tenantAware() || "GLOBAL".equals(item.scope()))
			return;
		if (!Objects.equals(tenant.organizationPublicId(), item.organizationPublicId())) {
			throw new AccessDeniedException("No tienes acceso a recursos de otra organización.");
		}
	}

	private void assertWritable(CatalogType type, Item item, TenantContext tenant) {
		if (tenant.globalAdministrator())
			return;
		if (!type.tenantAware() || "GLOBAL".equals(item.scope())) {
			throw new AccessDeniedException("No puedes modificar contenido GLOBAL.");
		}
		if (!Objects.equals(tenant.organizationPublicId(), item.organizationPublicId())) {
			throw new AccessDeniedException("No puedes modificar recursos de otra organización.");
		}
	}

	private void assertUnique(CatalogType type, String excludedId, String code, String name, ContentScope scope,
			String organizationPublicId) {
		String normalizedName = normalizeName(name);
		boolean duplicate = allItems(type, null).stream()
				.filter(value -> excludedId == null || !value.id().equals(excludedId))
				.filter(value -> Objects.equals(scope.name(), value.scope()))
				.filter(value -> scope == ContentScope.GLOBAL
						|| Objects.equals(organizationPublicId, value.organizationPublicId()))
				.anyMatch(value -> value.code().equalsIgnoreCase(code)
						|| normalizeName(value.name()).equals(normalizedName));
		if (duplicate) {
			throw new BusinessException("CATALOG_DUPLICATE",
					"Ya existe un registro con ese nombre o código dentro de la organización propietaria.");
		}
	}

	private long dependencyCount(CatalogType type, Object key) {
		String sql = switch (type) {
		case CATEGORIES -> "SELECT " + "(SELECT COUNT(*) FROM QUESTION WHERE CATEGORY_ID = :key) + "
				+ "(SELECT COUNT(*) FROM QUESTION_CATEGORY_RELATION WHERE CATEGORY_ID = :key) + "
				+ "(SELECT COUNT(*) FROM COLLECTION_CATEGORY_RELATION WHERE CATEGORY_ID = :key) + "
				+ "(SELECT COUNT(*) FROM FORM_QUESTION_POOL WHERE CATEGORY_ID = :key) + "
				+ "(SELECT COUNT(*) FROM QUESTION_CATEGORY WHERE SOURCE_GLOBAL_ID = :key) FROM DUAL";
		case TECHNOLOGIES -> "SELECT (SELECT COUNT(*) FROM QUESTION WHERE TECHNOLOGY_ID = :key) + "
				+ "(SELECT COUNT(*) FROM STUDENT WHERE TECHNOLOGY_ID = :key OR TALENT_TECHNOLOGY_ID = :key) + "
				+ "(SELECT COUNT(*) FROM STUDENT_CERTIFICATION_CYCLE WHERE TECHNOLOGY_ID = :key) + "
				+ "(SELECT COUNT(*) FROM STUDENT_CERTIFICATION_PROFILE profile "
				+ "JOIN CERTIFICATION_TECHNOLOGY_CATALOG certification "
				+ "ON certification.CERTIFICATION_TECHNOLOGY_ID = profile.CERTIFICATION_TECHNOLOGY_ID "
				+ "WHERE certification.MASTER_TECHNOLOGY_ID = :key) FROM DUAL";
		case PROFESSIONAL_PROFILES -> "SELECT "
				+ "(SELECT COUNT(*) FROM STUDENT WHERE PROFESSIONAL_PROFILE_ID = :key) + "
				+ "(SELECT COUNT(*) FROM STUDENT_CERTIFICATION_PROFILE WHERE PROFESSIONAL_PROFILE_ID = :key) FROM DUAL";
		case TECHNOLOGICAL_PROFILES -> "SELECT "
				+ "(SELECT COUNT(*) FROM STUDENT WHERE TECHNOLOGICAL_PROFILE_ID = :key) + "
				+ "(SELECT COUNT(*) FROM STUDENT_CERTIFICATION_PROFILE WHERE TECHNOLOGICAL_PROFILE = "
				+ "(SELECT PROFILE_CODE FROM TECHNOLOGICAL_PROFILE_CATALOG WHERE TECHNOLOGICAL_PROFILE_ID = :key)) + "
				+ "(SELECT COUNT(*) FROM CERTIFICATION_PROFILE_CATALOG WHERE SUGGESTED_TECH_PROFILE = "
				+ "(SELECT PROFILE_CODE FROM TECHNOLOGICAL_PROFILE_CATALOG WHERE TECHNOLOGICAL_PROFILE_ID = :key)) FROM DUAL";
		case QUESTION_TYPES -> "SELECT COUNT(*) FROM QUESTION WHERE TYPE_CODE = :key";
		case DIFFICULTIES -> "SELECT COUNT(*) FROM QUESTION WHERE DIFFICULTY_CODE = :key";
		};
		Long result = jdbc.queryForObject(sql, new MapSqlParameterSource("key", key), Long.class);
		return result == null ? 0 : result;
	}

	private Object dependencyKey(CatalogType type, String id) {
		return switch (type) {
		case CATEGORIES -> requireCategory(id).getId();
		case TECHNOLOGIES -> requireTechnology(id).getId();
		case PROFESSIONAL_PROFILES -> requireProfile(id).getId();
		case TECHNOLOGICAL_PROFILES -> requireTechnologicalProfile(id).getId();
		case QUESTION_TYPES, DIFFICULTIES -> id;
		};
	}

	private String dependencyLabel(CatalogType type, long count) {
		return switch (type) {
		case CATEGORIES -> count + " preguntas relacionadas";
		case TECHNOLOGIES -> count + " usos en preguntas o certificaciones";
		case PROFESSIONAL_PROFILES -> count + " perfiles de estudiantes";
		case TECHNOLOGICAL_PROFILES -> count + " perfiles o sugerencias";
		case QUESTION_TYPES, DIFFICULTIES -> count + " preguntas relacionadas";
		};
	}

	private QuestionCategoryJpaEntity requireCategory(String id) {
		return categories.findByPublicId(id).filter(value -> value.getStatus() != CatalogStatus.DELETED)
				.orElseThrow(this::notFound);
	}

	private QuestionTechnologyJpaEntity requireTechnology(String id) {
		return technologies.findByPublicId(id).orElseThrow(this::notFound);
	}

	private ProfessionalCertificationProfileJpaEntity requireProfile(String id) {
		return profiles.findByPublicId(id).orElseThrow(this::notFound);
	}

	private TechnologicalProfileCatalogJpaEntity requireTechnologicalProfile(String id) {
		return technologicalProfiles.findByPublicId(id).orElseThrow(this::notFound);
	}

	private QuestionTypeJpaEntity requireType(String id) {
		return types.findById(id).orElseThrow(this::notFound);
	}

	private QuestionDifficultyJpaEntity requireDifficulty(String id) {
		return difficulties.findById(id).orElseThrow(this::notFound);
	}

	private Item getWithoutSecurity(CatalogType type, String id) {
		return switch (type) {
		case CATEGORIES -> categoryItem(requireCategory(id));
		case TECHNOLOGIES -> technologyItem(requireTechnology(id));
		case PROFESSIONAL_PROFILES -> profileItem(requireProfile(id));
		case TECHNOLOGICAL_PROFILES -> technologicalProfileItem(requireTechnologicalProfile(id));
		case QUESTION_TYPES -> typeItem(requireType(id));
		case DIFFICULTIES -> difficultyItem(requireDifficulty(id));
		};
	}

	private BusinessException catalogInUse(long count) {
		return new BusinessException("CATALOG_IN_USE",
				"No es posible eliminar este registro porque actualmente está siendo utilizado por "
						+ count + (count == 1 ? " registro. " : " registros. ")
						+ "Puedes inactivarlo para evitar que esté disponible en nuevas asignaciones.");
	}

	private BusinessException catalogInUse() {
		return new BusinessException("CATALOG_IN_USE",
				"No es posible eliminar este registro porque actualmente está siendo utilizado. "
						+ "Puedes inactivarlo para evitar que esté disponible en nuevas asignaciones.");
	}

	private BusinessException notFound() {
		return new BusinessException("CATALOG_ITEM_NOT_FOUND", "El registro de catálogo solicitado no existe.");
	}

	private void validate(Upsert request) {
		if (request == null || request.code() == null || request.code().isBlank()) {
			throw new BusinessException("CATALOG_CODE_REQUIRED", "El código es obligatorio.");
		}
		if (request.name() == null || request.name().isBlank()) {
			throw new BusinessException("CATALOG_NAME_REQUIRED", "El nombre es obligatorio.");
		}
	}

	private String parseStatus(String status) {
		if (status == null || status.isBlank() || ACTIVE.equalsIgnoreCase(status))
			return ACTIVE;
		if ("ALL".equalsIgnoreCase(status))
			return null;
		return requireOperationalStatus(status);
	}

	private String requireOperationalStatus(String status) {
		String normalized = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
		if (!ACTIVE.equals(normalized) && !INACTIVE.equals(normalized)) {
			throw new BusinessException("CATALOG_STATUS_INVALID", "El estado indicado no es válido.");
		}
		return normalized;
	}

	private boolean matchesStatus(String current, String expected) {
		return expected == null || expected.equals(current);
	}

	private void requireTenant(TenantContext tenant) {
		if (tenant == null || (!tenant.globalAdministrator() && !tenant.hasOrganization())) {
			throw new AccessDeniedException("No se pudo resolver el contexto organizacional.");
		}
	}

	private void requireGlobalScopeAdministrator(TenantContext tenant) {
		if (tenant == null || !tenant.globalAdministrator() || !tenant.globalScope()) {
			throw new AccessDeniedException("Este catálogo solamente puede modificarse desde el contexto GLOBAL.");
		}
	}

	private void checkVersion(Long current, Long expected) {
		if (expected == null || !Objects.equals(current, expected)) {
			throw new BusinessException("CATALOG_CONCURRENT_MODIFICATION",
					"La información fue modificada por otra sesión. Actualiza la página.");
		}
	}

	private String normalizeCode(String value) {
		String result = Normalizer.normalize(value.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "")
				.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
		if (result.isBlank() || result.length() > 120) {
			throw new BusinessException("CATALOG_CODE_INVALID", "El código no es válido.");
		}
		return result;
	}

	private String normalizeName(String value) {
		return Normalizer.normalize(value == null ? "" : value.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "")
				.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
	}

	private String clean(String value, int max) {
		String result = value.trim().replaceAll("\\s+", " ");
		if (result.length() > max)
			throw new BusinessException("CATALOG_NAME_TOO_LONG", "El nombre es demasiado largo.");
		return result;
	}

	private String cleanOptional(String value, int max) {
		if (value == null || value.isBlank())
			return null;
		String result = value.trim();
		if (result.length() > max)
			throw new BusinessException("CATALOG_DESCRIPTION_TOO_LONG", "La descripción es demasiado larga.");
		return result;
	}

	private int order(Integer value) {
		return value == null ? 0 : Math.max(0, value);
	}

	private void synchronizeCertificationTechnology(QuestionTechnologyJpaEntity master, Instant now) {
		if (master.getContentScope() != ContentScope.GLOBAL)
			return;
		CertificationTechnologyJpaEntity bridge = certificationTechnologies.findByMasterTechnologyId(master.getId())
				.or(() -> certificationTechnologies.findByCodeIgnoreCase(master.getCode()))
				.orElseGet(() -> CertificationTechnologyJpaEntity.createFromMaster(UUID.randomUUID().toString(),
						master.getCode(), master.getName(), master.getStatus().name(), master.getDisplayOrder(),
						master.getId(), now));
		bridge.synchronizeWithMaster(master.getName(), master.getStatus().name(), master.getDisplayOrder(),
				master.getId(), now);
		certificationTechnologies.saveAndFlush(bridge);
	}

	private String parseSuggestedProfile(String value, OwnershipTarget owner) {
		if (value == null || value.isBlank())
			return null;
		String code = normalizeCode(value);
		TechnologicalProfileCatalogJpaEntity profile = technologicalProfiles
				.findVisible(owner.ownerOrganizationId(), ACTIVE).stream()
				.filter(candidate -> candidate.getCode().equalsIgnoreCase(code))
				.filter(candidate -> owner.scope() == ContentScope.ORGANIZATION
						|| candidate.getContentScope() == ContentScope.GLOBAL)
				.sorted(Comparator.comparing((TechnologicalProfileCatalogJpaEntity candidate) -> !Objects
						.equals(candidate.getOwnerOrganizationId(), owner.ownerOrganizationId())))
				.findFirst().orElseThrow(() -> new BusinessException("CATALOG_TECH_PROFILE_INVALID",
						"El perfil tecnológico sugerido no pertenece al contexto del perfil profesional."));
		if (!ACTIVE.equals(profile.getStatus())) {
			throw new BusinessException("CATALOG_TECH_PROFILE_INACTIVE",
					"El perfil tecnológico sugerido se encuentra inactivo.");
		}
		return profile.getCode();
	}

	private OrganizationJpaEntity organization(Long id) {
		return id == null ? null : organizations.findById(id).orElse(null);
	}

	private String scopeName(ContentScope scope) {
		return scope == ContentScope.GLOBAL ? "GLOBAL" : null;
	}

	private void record(Long actorId, String eventType, CatalogType type, Item item, String description,
			TenantContext tenant) {
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("catalogType", type.name());
		values.put("catalogItemId", item.id());
		values.put("code", item.code());
		values.put("name", item.name());
		values.put("status", item.status());
		values.put("scope", item.scope());
		values.put("actorOrganizationId", tenant.organizationPublicId());
		values.put("ownerOrganizationId", item.organizationPublicId());
		audit.record(actorId, eventType, "CATALOGS", description, null, null, values, clock.instant());
	}

	private record OwnershipTarget(ContentScope scope, Long ownerOrganizationId, String organizationPublicId) {
	}
}
