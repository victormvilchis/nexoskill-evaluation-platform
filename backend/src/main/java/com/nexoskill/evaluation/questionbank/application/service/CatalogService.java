package com.nexoskill.evaluation.questionbank.application.service;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.organizations.domain.model.TenantContext;
import com.nexoskill.evaluation.questionbank.application.model.*;
import com.nexoskill.evaluation.questionbank.application.port.out.QuestionCatalogPort;
import com.nexoskill.evaluation.questionbank.domain.model.CatalogStatus;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {
	private final QuestionCatalogPort port;
	private final AuditLogPort auditLogPort;
	private final Clock clock;

	public CatalogService(QuestionCatalogPort port, AuditLogPort auditLogPort, Clock clock) {
		this.port = port;
		this.auditLogPort = auditLogPort;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public QuestionCatalogs active(TenantContext tenant) {
		return port.activeCatalogs(tenant);
	}

	@Transactional(readOnly = true)
	public List<QuestionCategorySummary> categories(TenantContext tenant, CatalogStatus status) {
		return port.categories(tenant, status);
	}

	@Transactional(readOnly = true)
	public List<QuestionCategorySummary> questionOptions(TenantContext tenant, String questionPublicId,
			String targetScope, String organizationPublicId) {
		return port.questionOptions(tenant, questionPublicId, targetScope, organizationPublicId);
	}

	@Transactional(readOnly = true)
	public QuestionCategorySummary get(String publicId, TenantContext tenant) {
		return port.get(publicId, tenant);
	}

	@Transactional
	public QuestionCategorySummary create(CategoryCommands.Create command) {
		QuestionCategorySummary result = port.create(command);
		audit(command.actorUserId(), "CATEGORY_CREATED", result,
				"Se creó una categoría dentro del alcance " + result.contentScope() + ".");
		return result;
	}

	@Transactional
	public QuestionCategorySummary update(CategoryCommands.Update command) {
		QuestionCategorySummary result = port.update(command);
		audit(command.actorUserId(), "CATEGORY_UPDATED", result, "Se actualizaron los datos de la categoría.");
		return result;
	}

	@Transactional
	public QuestionCategorySummary status(CategoryCommands.ChangeStatus command) {
		QuestionCategorySummary result = port.changeStatus(command);
		audit(command.actorUserId(),
				result.status() == CatalogStatus.ACTIVE ? "CATEGORY_ACTIVATED" : "CATEGORY_DEACTIVATED", result,
				command.reason() == null ? "Cambio de estado de categoría." : command.reason());
		return result;
	}

	@Transactional
	public QuestionCategorySummary delete(CategoryCommands.Delete command) {
		QuestionCategorySummary result = port.softDelete(command);
		audit(command.actorUserId(), "CATEGORY_DELETED", result,
				command.reason() == null ? "Eliminación lógica de categoría." : command.reason());
		return result;
	}

	@Transactional(readOnly = true)
	public QuestionCategoryDependencies dependencies(String publicId, TenantContext tenant) {
		return port.dependencies(publicId, tenant);
	}

	@Transactional(readOnly = true)
	public List<QuestionCategoryStatusHistory> history(String publicId, TenantContext tenant) {
		return port.history(publicId, tenant);
	}

	private void audit(Long actorUserId, String eventType, QuestionCategorySummary category, String description) {
		auditLogPort.record(actorUserId, eventType, "QUESTION_CATEGORY", description, null, null,
				Map.of("categoryPublicId", category.publicId(), "status", category.status().name(), "scope",
						category.contentScope().name()),
				clock.instant());
	}
}
