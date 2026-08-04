package com.nexoskill.evaluation.questionbank.application.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.globalcontent.application.service.ContentPromotionService;
import com.nexoskill.evaluation.globalcontent.domain.model.GlobalContentType;
import com.nexoskill.evaluation.globalcontent.infrastructure.persistence.GlobalContentPromotionRepository;
import com.nexoskill.evaluation.organizations.application.TenantContextResolver;
import com.nexoskill.evaluation.organizations.domain.model.TenantContext;
import com.nexoskill.evaluation.questionbank.infrastructure.persistence.SpringDataQuestionRepository;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import org.junit.jupiter.api.Test;

class QuestionGlobalCloneServiceTest {
	private final SpringDataQuestionRepository questions = mock(SpringDataQuestionRepository.class);
	private final GlobalContentPromotionRepository promotions = mock(GlobalContentPromotionRepository.class);
	private final ContentPromotionService promotionService = mock(ContentPromotionService.class);
	private final TenantContextResolver tenantContextResolver = mock(TenantContextResolver.class);
	private final HttpServletRequest request = mock(HttpServletRequest.class);
	private final AuditLogPort audit = mock(AuditLogPort.class);
	private final QuestionGlobalCloneService service = new QuestionGlobalCloneService(questions, promotions,
			promotionService, tenantContextResolver, request, audit, Clock.systemUTC());

	@Test
	void globalAdministratorCanPrepareCloneWhileViewingAnOrganization() {
		when(tenantContextResolver.resolve(request))
				.thenReturn(TenantContext.organization(20L, "org-20", "ORG_20", true));

		assertThatCode(() -> service.preview("question-id")).doesNotThrowAnyException();

		verify(promotionService).preview(GlobalContentType.QUESTION, "question-id");
	}

	@Test
	void organizationalUserCannotPrepareCloneToGlobal() {
		when(tenantContextResolver.resolve(request))
				.thenReturn(TenantContext.organization(20L, "org-20", "ORG_20", false));

		assertThatThrownBy(() -> service.preview("question-id")).isInstanceOf(BusinessException.class)
				.hasMessage("Solo el Administrador global puede clonar preguntas al catálogo global.");
	}
}
