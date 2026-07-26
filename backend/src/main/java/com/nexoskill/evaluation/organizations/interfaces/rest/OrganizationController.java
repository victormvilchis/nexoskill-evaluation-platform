package com.nexoskill.evaluation.organizations.interfaces.rest;

import com.nexoskill.evaluation.organizations.application.OrganizationService;
import com.nexoskill.evaluation.organizations.domain.model.ContentMode;
import com.nexoskill.evaluation.organizations.domain.model.OrganizationStatus;
import com.nexoskill.evaluation.organizations.domain.model.OrganizationType;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationJpaEntity;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationLicensePolicyJpaEntity;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/organizations")
public class OrganizationController {
	private final OrganizationService service;

	public OrganizationController(OrganizationService service) {
		this.service = service;
	}

	@GetMapping
	@PreAuthorize("hasAuthority('ORGANIZATION_VIEW')")
	public PageResponse list(@RequestParam(required = false) String query,
			@RequestParam(defaultValue = "ACTIVE") String status, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		OrganizationStatus parsedStatus = parseStatus(status);
		Page<OrganizationJpaEntity> result = service.search(query, parsedStatus, page, size);
		return new PageResponse(result.getContent().stream().map(this::summary).toList(), result.getNumber(),
				result.getSize(), result.getTotalElements(), result.getTotalPages());
	}

	@GetMapping("/{publicId}")
	@PreAuthorize("hasAuthority('ORGANIZATION_VIEW')")
	public OrganizationResponse get(@PathVariable String publicId) {
		return response(service.get(publicId));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasAuthority('ORGANIZATION_CREATE')")
	public OrganizationResponse create(@Valid @RequestBody CreateRequest request) {
		return response(service.create(new OrganizationService.CreateCommand(request.name(), request.code(),
				request.contentMode(), request.expiresOn(), request.contractedSeats(), request.includedReplacements(),
				request.additionalReplacements(), request.standardReleaseHours(), request.exhaustedReleaseDays(),
				request.cycleStartsOn(), request.cycleEndsOn())));
	}

	@PutMapping("/{publicId}")
	@PreAuthorize("hasAuthority('ORGANIZATION_UPDATE')")
	public OrganizationResponse update(@PathVariable String publicId, @Valid @RequestBody UpdateRequest request) {
		return response(service.update(publicId,
				new OrganizationService.UpdateCommand(request.name(), request.contentMode(), request.expiresOn(),
						request.contractedSeats(), request.includedReplacements(), request.additionalReplacements(),
						request.standardReleaseHours(), request.exhaustedReleaseDays(), request.cycleStartsOn(),
						request.cycleEndsOn(), request.version())));
	}

	@PostMapping("/{publicId}/status/{status}")
	@PreAuthorize("hasAuthority('ORGANIZATION_STATUS_CHANGE')")
	public OrganizationResponse changeStatus(@PathVariable String publicId, @PathVariable OrganizationStatus status) {
		return response(service.changeStatus(publicId, status));
	}

	private OrganizationStatus parseStatus(String value) {
		if (value == null || value.isBlank() || "ACTIVE".equalsIgnoreCase(value))
			return OrganizationStatus.ACTIVE;
		if ("ALL".equalsIgnoreCase(value))
			return null;
		try {
			return OrganizationStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException exception) {
			throw new BusinessException("ORGANIZATION_STATUS_INVALID", "El estado indicado no es válido.");
		}
	}

	private OrganizationSummary summary(OrganizationJpaEntity entity) {
		return new OrganizationSummary(entity.getPublicId(), entity.getCode(), entity.getName(),
				entity.getOrganizationType(), entity.getStatus(), entity.getContentMode(), entity.getExpiresOn(),
				entity.getUpdatedAt());
	}

	private OrganizationResponse response(OrganizationService.OrganizationAggregate aggregate) {
		OrganizationJpaEntity organization = aggregate.organization();
		OrganizationLicensePolicyJpaEntity policy = aggregate.policy();
		return new OrganizationResponse(organization.getPublicId(), organization.getCode(), organization.getName(),
				organization.getOrganizationType(), organization.getStatus(), organization.getContentMode(),
				organization.getValidFrom(), organization.getExpiresOn(),
				policy == null ? null : policy.getContractedSeats(),
				policy == null ? null : policy.getIncludedReplacements(),
				policy == null ? null : policy.getAdditionalReplacements(),
				policy == null ? null : policy.getStandardReleaseHours(),
				policy == null ? null : policy.getExhaustedReleaseDays(),
				policy == null ? null : policy.getCycleStartsOn(), policy == null ? null : policy.getCycleEndsOn(),
				organization.getCreatedAt(), organization.getUpdatedAt(), organization.getVersion());
	}

	public record CreateRequest(
			@NotBlank(message = "El nombre es obligatorio.") @Size(max = 200, message = "El nombre no puede superar 200 caracteres.") String name,
			@NotBlank(message = "El código es obligatorio.") @Size(max = 80, message = "El código no puede superar 80 caracteres.") String code,
			@NotNull(message = "Selecciona una modalidad de contenido.") ContentMode contentMode, LocalDate expiresOn,
			@NotNull(message = "Los asientos contratados son obligatorios.") @Min(value = 0, message = "Los asientos contratados no pueden ser negativos.") Integer contractedSeats,
			@Min(value = 0, message = "Las sustituciones incluidas no pueden ser negativas.") Integer includedReplacements,
			@Min(value = 0, message = "Las sustituciones adicionales no pueden ser negativas.") Integer additionalReplacements,
			@Min(value = 1, message = "La liberación estándar debe ser mayor a cero.") Integer standardReleaseHours,
			@Min(value = 0, message = "El bloqueo antifraude no puede ser negativo.") Integer exhaustedReleaseDays,
			LocalDate cycleStartsOn, LocalDate cycleEndsOn) {
	}

	public record UpdateRequest(
			@NotBlank(message = "El nombre es obligatorio.") @Size(max = 200, message = "El nombre no puede superar 200 caracteres.") String name,
			@NotNull(message = "Selecciona una modalidad de contenido.") ContentMode contentMode, LocalDate expiresOn,
			@NotNull(message = "Los asientos contratados son obligatorios.") @Min(value = 0, message = "Los asientos contratados no pueden ser negativos.") Integer contractedSeats,
			@NotNull(message = "Las sustituciones incluidas son obligatorias.") @Min(value = 0, message = "Las sustituciones incluidas no pueden ser negativas.") Integer includedReplacements,
			@NotNull(message = "Las sustituciones adicionales son obligatorias.") @Min(value = 0, message = "Las sustituciones adicionales no pueden ser negativas.") Integer additionalReplacements,
			@NotNull(message = "La liberación estándar es obligatoria.") @Min(value = 1, message = "La liberación estándar debe ser mayor a cero.") Integer standardReleaseHours,
			@NotNull(message = "El bloqueo antifraude es obligatorio.") @Min(value = 0, message = "El bloqueo antifraude no puede ser negativo.") Integer exhaustedReleaseDays,
			@NotNull(message = "El inicio de ciclo es obligatorio.") LocalDate cycleStartsOn,
			@NotNull(message = "El fin de ciclo es obligatorio.") LocalDate cycleEndsOn,
			@NotNull(message = "La versión de la organización es obligatoria.") Long version) {
	}

	public record OrganizationSummary(String publicId, String code, String name, OrganizationType organizationType,
			OrganizationStatus status, ContentMode contentMode, LocalDate expiresOn, java.time.Instant updatedAt) {
	}

	public record OrganizationResponse(String publicId, String code, String name, OrganizationType organizationType,
			OrganizationStatus status, ContentMode contentMode, LocalDate validFrom, LocalDate expiresOn,
			Integer contractedSeats, Integer includedReplacements, Integer additionalReplacements,
			Integer standardReleaseHours, Integer exhaustedReleaseDays, LocalDate cycleStartsOn, LocalDate cycleEndsOn,
			java.time.Instant createdAt, java.time.Instant updatedAt, Long version) {
	}

	public record PageResponse(List<OrganizationSummary> content, int page, int size, long totalElements,
			int totalPages) {
	}
}
