package com.nexoskill.evaluation.globalcontent.application.service;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.globalcontent.application.model.GlobalContentModels.*;
import com.nexoskill.evaluation.globalcontent.application.port.out.GlobalContentResourcePort;
import com.nexoskill.evaluation.globalcontent.application.port.out.GlobalContentResourcePort.ResourceKey;
import com.nexoskill.evaluation.globalcontent.domain.model.*;
import com.nexoskill.evaluation.globalcontent.infrastructure.persistence.*;
import com.nexoskill.evaluation.organizations.domain.model.ContentScope;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationJpaEntity;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationRepository;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContentPromotionService {
	private final GlobalContentResourcePort resources;
	private final GlobalContentPromotionRepository promotions;
	private final GlobalContentVersionRepository versions;
	private final OrganizationRepository organizations;
	private final AuditLogPort audit;
	private final Clock clock;

	public ContentPromotionService(GlobalContentResourcePort resources, GlobalContentPromotionRepository promotions,
			GlobalContentVersionRepository versions, OrganizationRepository organizations, AuditLogPort audit,
			Clock clock) {
		this.resources = resources;
		this.promotions = promotions;
		this.versions = versions;
		this.organizations = organizations;
		this.audit = audit;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public PromotionPreview preview(GlobalContentType type, String sourcePublicId) {
		ContentResource source = resources.find(type, sourcePublicId);
		validateSource(source);
		List<Dependency> dependencies = resources.dependencies(type, source.internalId());
		List<String> warnings = new ArrayList<>();
		for (Dependency dependency : dependencies) {
			if (dependency.scope() == ContentScope.ORGANIZATION && !dependency.globalEquivalentAvailable()) {
				warnings.add("La dependencia «" + dependency.name() + "» también deberá promoverse.");
			}
		}
		List<ContentResource> duplicates = resources.possibleGlobalDuplicates(source);
		if (!duplicates.isEmpty()) {
			warnings.add("Se detectaron posibles coincidencias en el catálogo global.");
		}
		return new PromotionPreview(source, dependencies, duplicates, true, List.copyOf(warnings));
	}

	@Transactional
	public PromotionView promote(PromoteCommand command, Long actorUserId) {
		Objects.requireNonNull(command, "command");
		return promoteInternal(command, actorUserId, new LinkedHashSet<>(), new HashMap<>());
	}

	@Transactional(readOnly = true)
	public List<PromotionView> list() {
		return promotions.findAllByOrderByPromotedAtDesc().stream().map(this::view).toList();
	}

	@Transactional(readOnly = true)
	public PromotionView get(String promotionPublicId) {
		return view(findPromotion(promotionPublicId));
	}

	private PromotionView promoteInternal(PromoteCommand command, Long actorUserId, Set<ResourceKey> stack,
			Map<ResourceKey, GlobalContentPromotionJpaEntity> promotedDuringOperation) {
		ContentResource source = resources.find(command.contentType(), command.sourcePublicId());
		validateSource(source);
		ResourceKey sourceKey = new ResourceKey(source.contentType(), source.internalId());
		if (!stack.add(sourceKey)) {
			throw new BusinessException("GLOBAL_CONTENT_DEPENDENCY_CYCLE",
					"Se detectó un ciclo entre las dependencias del contenido.");
		}
		try {
			Optional<GlobalContentPromotionJpaEntity> existing = promotions
					.findByContentTypeAndSourceContentIdAndSourceVersion(source.contentType(), source.internalId(),
							source.version());
			if (existing.isPresent())
				return view(existing.get());
			if (promotedDuringOperation.containsKey(sourceKey))
				return view(promotedDuringOperation.get(sourceKey));

			Map<ResourceKey, Long> dependencyTargets = new LinkedHashMap<>();
			List<Dependency> dependencies = resources.dependencies(source.contentType(), source.internalId());
			for (Dependency dependency : dependencies) {
				ResourceKey dependencyKey = new ResourceKey(dependency.contentType(), dependency.internalId());
				if (dependency.scope() == ContentScope.GLOBAL) {
					dependencyTargets.put(dependencyKey, dependency.internalId());
					continue;
				}
				Optional<GlobalContentPromotionJpaEntity> dependencyPromotion = promotions
						.findByContentTypeAndSourceContentIdAndSourceVersion(dependency.contentType(),
								dependency.internalId(), dependency.version());
				if (dependencyPromotion.isEmpty()) {
					if (!command.includeDependencies()) {
						throw new BusinessException("GLOBAL_CONTENT_DEPENDENCIES_MISSING",
								"El contenido tiene dependencias organizacionales que aún no existen en GLOBAL.");
					}
					PromotionView created = promoteInternal(
							new PromoteCommand(dependency.contentType(), dependency.publicId(), true,
									DuplicateResolution.CREATE_DISTINCT, null,
									"Promoción automática como dependencia de " + source.name()),
							actorUserId, stack, promotedDuringOperation);
					dependencyPromotion = promotions.findByPublicId(created.publicId());
				}
				dependencyTargets.put(dependencyKey, dependencyPromotion.orElseThrow().getGlobalContentId());
			}

			DuplicateResolution resolution = command.duplicateResolution() == null ? DuplicateResolution.CREATE_DISTINCT
					: command.duplicateResolution();
			if (resolution == DuplicateResolution.CANCEL) {
				throw new BusinessException("GLOBAL_CONTENT_PROMOTION_CANCELLED", "La promoción fue cancelada.");
			}

			ContentResource global;
			long globalVersion;
			if (resolution == DuplicateResolution.USE_EXISTING) {
				if (command.existingGlobalPublicId() == null || command.existingGlobalPublicId().isBlank()) {
					throw new BusinessException("GLOBAL_CONTENT_EXISTING_REQUIRED",
							"Selecciona el contenido global existente que deseas utilizar.");
				}
				global = resources.find(source.contentType(), command.existingGlobalPublicId());
				if (global.scope() != ContentScope.GLOBAL) {
					throw new BusinessException("GLOBAL_CONTENT_SCOPE_REQUIRED",
							"El recurso seleccionado no pertenece al catálogo global.");
				}
				globalVersion = versions
						.findFirstByContentTypeAndContentIdOrderByVersionNumberDesc(source.contentType(),
								global.internalId())
						.map(value -> value.getVersionNumber()).orElse(Math.max(1, global.version()));
			} else {
				global = resources.copyToGlobal(source.contentType(), source.internalId(), dependencyTargets,
						actorUserId);
				if (resolution == DuplicateResolution.CREATE_NEW_VERSION && command.existingGlobalPublicId() != null
						&& !command.existingGlobalPublicId().isBlank()) {
					ContentResource lineage = resources.find(source.contentType(), command.existingGlobalPublicId());
					globalVersion = versions
							.findFirstByContentTypeAndContentIdOrderByVersionNumberDesc(source.contentType(),
									lineage.internalId())
							.map(value -> value.getVersionNumber() + 1).orElse(Math.max(1, lineage.version()) + 1);
				} else {
					globalVersion = 1L;
				}
			}

			var promotion = promotions.saveAndFlush(GlobalContentPromotionJpaEntity.create(UUID.randomUUID().toString(),
					source.ownerOrganizationId(), source.contentType(), source.internalId(), source.publicId(),
					source.version(), source.createdBy(), global.internalId(), global.publicId(), globalVersion,
					trim(command.notes()), source.functionalHash(), actorUserId, clock.instant()));
			var version = versions.save(GlobalContentVersionJpaEntity.create(UUID.randomUUID().toString(),
					source.contentType(), global.internalId(), global.publicId(), globalVersion, promotion.getId(),
					global.functionalHash(), trim(command.notes()), actorUserId, clock.instant()));
			promotedDuringOperation.put(sourceKey, promotion);
			audit.record(actorUserId, "GLOBAL_CONTENT_PROMOTED", "GLOBAL_CONTENT",
					"Contenido organizacional promovido como copia global.", null, null,
					Map.of("promotionPublicId", promotion.getPublicId(), "contentType", source.contentType().name(),
							"sourceOrganizationId", source.ownerOrganizationId(), "sourceContentPublicId",
							source.publicId(), "globalContentPublicId", global.publicId(), "globalVersion",
							version.getVersionNumber()),
					clock.instant());
			return view(promotion);
		} finally {
			stack.remove(sourceKey);
		}
	}

	private void validateSource(ContentResource source) {
		if (source.scope() != ContentScope.ORGANIZATION || source.ownerOrganizationId() == null) {
			throw new BusinessException("GLOBAL_CONTENT_ORGANIZATIONAL_SOURCE_REQUIRED",
					"La promoción requiere contenido propiedad de una organización comercial.");
		}
		if ("DELETED".equalsIgnoreCase(source.status())) {
			throw new BusinessException("GLOBAL_CONTENT_SOURCE_DELETED", "No se puede promover contenido eliminado.");
		}
	}

	PromotionView view(GlobalContentPromotionJpaEntity promotion) {
		ContentResource source = resources.findByInternalId(promotion.getContentType(), promotion.getSourceContentId());
		OrganizationJpaEntity organization = organizations.findById(promotion.getSourceOrganizationId()).orElseThrow(
				() -> new BusinessException("ORGANIZATION_NOT_FOUND", "La organización de origen ya no existe."));
		return new PromotionView(promotion.getPublicId(), promotion.getContentType(), organization.getPublicId(),
				organization.getName(), promotion.getSourceContentPublicId(), source.name(),
				promotion.getSourceVersion(), promotion.getGlobalContentPublicId(), promotion.getGlobalVersion(),
				promotion.getStatus(), promotion.getNotes(), promotion.getPromotedBy(), promotion.getPromotedAt(),
				promotion.getReviewedBy(), promotion.getReviewedAt(), promotion.getPublishedBy(),
				promotion.getPublishedAt(), promotion.getRejectionReason(),
				resources.dependencies(promotion.getContentType(), promotion.getGlobalContentId()));
	}

	GlobalContentPromotionJpaEntity findPromotion(String publicId) {
		try {
			String normalized = UUID.fromString(publicId.trim()).toString();
			return promotions.findByPublicId(normalized)
					.orElseThrow(() -> new BusinessException("GLOBAL_CONTENT_PROMOTION_NOT_FOUND",
							"La promoción solicitada no existe."));
		} catch (IllegalArgumentException exception) {
			throw new BusinessException("GLOBAL_CONTENT_PROMOTION_ID_INVALID",
					"El identificador de promoción no es válido.");
		}
	}

	private static String trim(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
