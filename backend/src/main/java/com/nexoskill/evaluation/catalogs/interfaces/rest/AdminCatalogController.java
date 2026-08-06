package com.nexoskill.evaluation.catalogs.interfaces.rest;

import com.nexoskill.evaluation.authentication.infrastructure.security.AuthenticatedUser;
import com.nexoskill.evaluation.catalogs.application.CatalogAdministrationService;
import com.nexoskill.evaluation.catalogs.application.CatalogModels.*;
import com.nexoskill.evaluation.catalogs.domain.CatalogType;
import com.nexoskill.evaluation.organizations.application.TenantContextResolver;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/catalogs")
public class AdminCatalogController {
	private final CatalogAdministrationService service;
	private final TenantContextResolver tenantContextResolver;

	public AdminCatalogController(CatalogAdministrationService service, TenantContextResolver tenantContextResolver) {
		this.service = service;
		this.tenantContextResolver = tenantContextResolver;
	}

	@GetMapping
	@PreAuthorize("hasRole('ADMINISTRATOR') or hasAuthority('CATALOG_VIEW')")
	public List<TypeSummary> types(HttpServletRequest request) {
		return service.types(tenantContextResolver.resolve(request));
	}

	@GetMapping("/{type}")
	@PreAuthorize("hasRole('ADMINISTRATOR') or hasAuthority('CATALOG_VIEW')")
	public List<Item> items(@PathVariable String type, @RequestParam(defaultValue = "ACTIVE") String status,
			@RequestParam(required = false) String organizationPublicId, HttpServletRequest request) {
		return service.items(parse(type), status, organizationPublicId, tenantContextResolver.resolve(request));
	}

	@GetMapping("/{type}/{id}")
	@PreAuthorize("hasRole('ADMINISTRATOR') or hasAuthority('CATALOG_VIEW')")
	public Item get(@PathVariable String type, @PathVariable String id, HttpServletRequest request) {
		return service.get(parse(type), id, tenantContextResolver.resolve(request));
	}

	@PostMapping("/{type}")
	@PreAuthorize("hasRole('ADMINISTRATOR') or hasAnyAuthority('CATALOG_CREATE','CATALOG_MANAGE')")
	public ResponseEntity<Item> create(@PathVariable String type, @Valid @RequestBody UpsertRequest body,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
		Item result = service.create(parse(type), body.toCommand(), actor.internalId(),
				tenantContextResolver.resolve(request));
		return ResponseEntity.status(HttpStatus.CREATED).body(result);
	}

	@PutMapping("/{type}/{id}")
	@PreAuthorize("hasRole('ADMINISTRATOR') or hasAnyAuthority('CATALOG_UPDATE','CATALOG_MANAGE')")
	public Item update(@PathVariable String type, @PathVariable String id, @Valid @RequestBody UpsertRequest body,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
		return service.update(parse(type), id, body.toCommand(), actor.internalId(),
				tenantContextResolver.resolve(request));
	}

	@PostMapping("/{type}/{id}/{action:activate|deactivate}")
	@PreAuthorize("hasRole('ADMINISTRATOR') or hasAnyAuthority('CATALOG_STATUS_CHANGE','CATALOG_MANAGE')")
	public Item status(@PathVariable String type, @PathVariable String id, @PathVariable String action,
			@Valid @RequestBody StatusRequest body, @AuthenticationPrincipal AuthenticatedUser actor,
			HttpServletRequest request) {
		return service.changeStatus(parse(type), id, "activate".equals(action) ? "ACTIVE" : "INACTIVE",
				body.expectedVersion(), actor.internalId(), tenantContextResolver.resolve(request));
	}

	@GetMapping("/{type}/{id}/dependencies")
	@PreAuthorize("hasRole('ADMINISTRATOR') or hasAuthority('CATALOG_VIEW')")
	public Dependencies dependencies(@PathVariable String type, @PathVariable String id, HttpServletRequest request) {
		return service.dependencies(parse(type), id, tenantContextResolver.resolve(request));
	}

	@DeleteMapping("/{type}/{id}")
	@PreAuthorize("hasRole('ADMINISTRATOR') or hasAnyAuthority('CATALOG_DELETE','CATALOG_MANAGE')")
	public ResponseEntity<Void> delete(@PathVariable String type, @PathVariable String id,
			@Valid @RequestBody DeleteRequest body, @AuthenticationPrincipal AuthenticatedUser actor,
			HttpServletRequest request) {
		service.delete(parse(type), id, body.expectedVersion(), actor.internalId(),
				tenantContextResolver.resolve(request));
		return ResponseEntity.noContent().build();
	}

	private CatalogType parse(String value) {
		try {
			return CatalogType.valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
		} catch (Exception exception) {
			throw new BusinessException("CATALOG_TYPE_INVALID", "El tipo de catálogo indicado no es válido.");
		}
	}

	public record UpsertRequest(
			@NotBlank(message = "El código es obligatorio.") @Size(max = 120, message = "El código no puede superar 120 caracteres.") String code,
			@NotBlank(message = "El nombre es obligatorio.") @Size(max = 200, message = "El nombre no puede superar 200 caracteres.") String name,
			@Size(max = 500, message = "La descripción no puede superar 500 caracteres.") String description,
			Integer displayOrder, String organizationPublicId, String suggestedTechnologicalProfile,
			Long expectedVersion) {
		Upsert toCommand() {
			return new Upsert(code, name, description, displayOrder, organizationPublicId,
					suggestedTechnologicalProfile, expectedVersion);
		}
	}

	public record StatusRequest(Long expectedVersion) {
	}

	public record DeleteRequest(Long expectedVersion) {
	}
}
