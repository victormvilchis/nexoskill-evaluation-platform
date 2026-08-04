package com.nexoskill.evaluation.globalcontent.application.service;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.globalcontent.application.model.GlobalContentModels.*;
import com.nexoskill.evaluation.globalcontent.application.port.out.GlobalContentResourcePort;
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
public class ContentDistributionService {
	private final ContentDistributionJobRepository jobs;
	private final ContentDistributionResultRepository results;
	private final ContentDistributionWorker worker;
	private final GlobalContentResourcePort resources;
	private final GlobalContentVersionService versions;
	private final OrganizationRepository organizations;
	private final OrganizationGlobalContentGrantRepository grants;
	private final AuditLogPort audit;
	private final Clock clock;

	public ContentDistributionService(ContentDistributionJobRepository jobs,
			ContentDistributionResultRepository results, ContentDistributionWorker worker,
			GlobalContentResourcePort resources, GlobalContentVersionService versions,
			OrganizationRepository organizations, OrganizationGlobalContentGrantRepository grants, AuditLogPort audit,
			Clock clock) {
		this.jobs = jobs;
		this.results = results;
		this.worker = worker;
		this.resources = resources;
		this.versions = versions;
		this.organizations = organizations;
		this.grants = grants;
		this.audit = audit;
		this.clock = clock;
	}

	public DistributionJobView distribute(DistributionCommand command, Long actorUserId) {
		validate(command);
		ContentResource global = resources.find(command.contentType(), command.globalContentPublicId());
		if (global.scope() != ContentScope.GLOBAL) {
			throw new BusinessException("GLOBAL_CONTENT_SCOPE_REQUIRED",
					"Solo el contenido global puede distribuirse.");
		}
		if (!versions.isPublished(command.contentType(), global.internalId(), command.globalVersion())) {
			throw new BusinessException("GLOBAL_CONTENT_NOT_PUBLISHED",
					"Solo el contenido global publicado puede distribuirse.");
		}

		LinkedHashSet<String> destinations = new LinkedHashSet<>();
		for (String publicId : command.organizationPublicIds()) {
			try {
				destinations.add(UUID.fromString(publicId.trim()).toString());
			} catch (Exception exception) {
				throw new BusinessException("ORGANIZATION_ID_INVALID",
						"Uno de los identificadores de organización no es válido.");
			}
		}
		LinkedHashMap<String, OrganizationJpaEntity> resolvedDestinations = new LinkedHashMap<>();
		for (String organizationPublicId : destinations) {
			OrganizationJpaEntity organization = organizations.findByPublicId(organizationPublicId)
					.orElseThrow(() -> new BusinessException("ORGANIZATION_NOT_FOUND",
							"Una de las organizaciones destino no existe."));
			resolvedDestinations.put(organizationPublicId, organization);
		}

		ContentDistributionJobJpaEntity job = jobs.saveAndFlush(
				ContentDistributionJobJpaEntity.create(UUID.randomUUID().toString(), command.contentType(),
						global.internalId(), global.publicId(), command.globalVersion(), command.distributionMode(),
						resolvedDestinations.size(), actorUserId, trim(command.notes()), clock.instant()));
		job.start(clock.instant());
		jobs.saveAndFlush(job);

		for (Map.Entry<String, OrganizationJpaEntity> destination : resolvedDestinations.entrySet()) {
			String organizationPublicId = destination.getKey();
			OrganizationJpaEntity organization = destination.getValue();
			try {
				GrantCommand grantCommand = new GrantCommand(organizationPublicId, command.contentType(),
						global.publicId(), command.globalVersion(), command.distributionMode(), command.accessMode(),
						command.cloningAllowed(), command.organizationEditable(), command.updatePolicy(),
						command.availableFrom(), command.expiresAt());
				var outcome = worker.process(grantCommand, actorUserId);
				Long targetId = outcome.target() == null ? null : outcome.target().internalId();
				String targetPublicId = outcome.target() == null ? null : outcome.target().publicId();
				if (outcome.skipped()) {
					results.save(ContentDistributionResultJpaEntity.skipped(job.getId(), organization.getId(),
							outcome.grantId(), targetId, targetPublicId, clock.instant()));
					job.registerSkipped();
				} else {
					results.save(ContentDistributionResultJpaEntity.success(job.getId(), organization.getId(),
							outcome.grantId(), targetId, targetPublicId, clock.instant()));
					job.registerSuccess();
				}
			} catch (BusinessException exception) {
				results.save(ContentDistributionResultJpaEntity.failed(job.getId(), organization.getId(),
						exception.getCode(), exception.getMessage(), clock.instant()));
				job.registerFailure();
			} catch (RuntimeException exception) {
				results.save(ContentDistributionResultJpaEntity.failed(job.getId(), organization.getId(),
						"DISTRIBUTION_UNEXPECTED_ERROR", "No fue posible procesar la organización destino.",
						clock.instant()));
				job.registerFailure();
			}
			jobs.saveAndFlush(job);
		}

		job.finish(clock.instant());
		jobs.saveAndFlush(job);
		audit.record(actorUserId, "GLOBAL_CONTENT_DISTRIBUTION_COMPLETED", "GLOBAL_CONTENT",
				"Proceso de distribución global finalizado.", null, null,
				Map.of("jobPublicId", job.getPublicId(), "contentType", command.contentType().name(),
						"globalContentPublicId", global.publicId(), "globalVersion", command.globalVersion(), "total",
						job.getTotalOrganizations(), "success", job.getSuccessfulOrganizations(), "skipped",
						job.getSkippedOrganizations(), "failed", job.getFailedOrganizations()),
				clock.instant());
		return view(job);
	}

	@Transactional(readOnly = true)
	public DistributionJobView get(String publicId) {
		try {
			ContentDistributionJobJpaEntity job = jobs.findByPublicId(UUID.fromString(publicId.trim()).toString())
					.orElseThrow(() -> new BusinessException("GLOBAL_CONTENT_DISTRIBUTION_NOT_FOUND",
							"El proceso de distribución no existe."));
			return view(job);
		} catch (IllegalArgumentException exception) {
			throw new BusinessException("GLOBAL_CONTENT_DISTRIBUTION_ID_INVALID",
					"El identificador del proceso no es válido.");
		}
	}

	private DistributionJobView view(ContentDistributionJobJpaEntity job) {
		List<DistributionResult> resultViews = results.findAllByJobIdOrderByProcessedAtAsc(job.getId()).stream()
				.map(result -> {
					OrganizationJpaEntity organization = organizations.findById(result.getOrganizationId())
							.orElse(null);
					OrganizationGlobalContentGrantJpaEntity grant = result.getGrantId() == null ? null
							: grants.findById(result.getGrantId()).orElse(null);
					return new DistributionResult(organization == null ? null : organization.getPublicId(),
							organization == null ? "Organización no encontrada" : organization.getName(),
							result.getStatus(), grant == null ? null : grant.getPublicId(),
							result.getTargetContentPublicId(), result.getErrorCode(), result.getErrorMessage(),
							result.getAttemptNumber(), result.getProcessedAt());
				}).toList();
		return new DistributionJobView(job.getPublicId(), job.getContentType(), job.getGlobalContentPublicId(),
				job.getGlobalVersion(), job.getDistributionMode(), job.getStatus(), job.getTotalOrganizations(),
				job.getProcessedOrganizations(), job.getSuccessfulOrganizations(), job.getFailedOrganizations(),
				job.getSkippedOrganizations(), job.getRequestedAt(), job.getStartedAt(), job.getFinishedAt(),
				resultViews);
	}

	private void validate(DistributionCommand command) {
		if (command == null || command.contentType() == null || command.distributionMode() == null
				|| command.accessMode() == null || command.updatePolicy() == null) {
			throw new BusinessException("GLOBAL_CONTENT_DISTRIBUTION_INVALID",
					"Completa la configuración de distribución.");
		}
		if (command.organizationPublicIds() == null || command.organizationPublicIds().isEmpty()) {
			throw new BusinessException("GLOBAL_CONTENT_DESTINATIONS_REQUIRED",
					"Selecciona al menos una organización destino.");
		}
		if (command.globalVersion() <= 0) {
			throw new BusinessException("GLOBAL_CONTENT_VERSION_INVALID", "La versión global debe ser mayor a cero.");
		}
		if (command.availableFrom() != null && command.expiresAt() != null
				&& !command.expiresAt().isAfter(command.availableFrom())) {
			throw new BusinessException("GLOBAL_CONTENT_GRANT_DATES_INVALID",
					"La fecha de vencimiento debe ser posterior a la disponibilidad.");
		}
	}

	private static String trim(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
