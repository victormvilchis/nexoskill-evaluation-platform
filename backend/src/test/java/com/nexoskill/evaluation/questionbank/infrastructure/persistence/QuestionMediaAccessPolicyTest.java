package com.nexoskill.evaluation.questionbank.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexoskill.evaluation.globalcontent.application.service.GlobalContentAccessPolicy;
import com.nexoskill.evaluation.globalcontent.domain.model.GlobalContentType;
import com.nexoskill.evaluation.organizations.domain.model.ContentScope;
import com.nexoskill.evaluation.organizations.domain.model.TenantContext;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QuestionMediaAccessPolicyTest {
	private SpringDataQuestionRepository questions;
	private GlobalContentAccessPolicy contentAccessPolicy;
	private QuestionMediaAccessPolicy policy;

	@BeforeEach
	void setUp() {
		questions = mock(SpringDataQuestionRepository.class);
		contentAccessPolicy = mock(GlobalContentAccessPolicy.class);
		policy = new QuestionMediaAccessPolicy(questions, contentAccessPolicy);
	}

	@Test
	void allowsMediaOwnedByCurrentOrganization() {
		QuestionMediaJpaEntity media = media(ContentScope.ORGANIZATION, 20L, 7L);

		policy.assertAccessible(media, 8L, TenantContext.organization(20L, "org-20", "ORG20", false));

		verify(questions, never()).findAllUsingMedia(any());
	}

	@Test
	void hidesMediaOwnedByAnotherOrganization() {
		QuestionMediaJpaEntity media = media(ContentScope.ORGANIZATION, 20L, 7L);
		when(questions.findAllUsingMedia(90L)).thenReturn(List.of());

		assertThatThrownBy(
				() -> policy.assertAccessible(media, 8L, TenantContext.organization(30L, "org-30", "ORG30", false)))
				.isInstanceOfSatisfying(BusinessException.class, exception -> org.assertj.core.api.Assertions
						.assertThat(exception.getCode()).isEqualTo("QUESTION_MEDIA_NOT_FOUND"));
	}

	@Test
	void allowsGlobalMediaOnlyWhenUsedByAQuestionReadableInTheTenant() {
		QuestionMediaJpaEntity media = media(ContentScope.GLOBAL, 1L, 7L);
		QuestionJpaEntity question = mock(QuestionJpaEntity.class);
		when(question.getId()).thenReturn(101L);
		when(question.getContentScope()).thenReturn(ContentScope.GLOBAL);
		when(question.getOwnerOrganizationId()).thenReturn(1L);
		when(questions.findAllUsingMedia(90L)).thenReturn(List.of(question));
		TenantContext tenant = TenantContext.organization(30L, "org-30", "ORG30", false);
		when(contentAccessPolicy.canRead(GlobalContentType.QUESTION, 101L, ContentScope.GLOBAL, 1L, tenant))
				.thenReturn(true);

		policy.assertAccessible(media, 8L, tenant);

		verify(contentAccessPolicy).canRead(GlobalContentType.QUESTION, 101L, ContentScope.GLOBAL, 1L, tenant);
	}

	@Test
	void globalAdministratorMayReadMediaOnlyFromGlobalContext() {
		QuestionMediaJpaEntity media = media(ContentScope.ORGANIZATION, 20L, 7L);

		policy.assertAccessible(media, 1L, TenantContext.global(1L, "global", "GLOBAL"));

		verify(questions, never()).findAllUsingMedia(any());
	}

	@Test
	void preventsOrganizationalMediaFromBeingAttachedToGlobalContent() {
		QuestionMediaJpaEntity media = media(ContentScope.ORGANIZATION, 20L, 7L);
		TenantContext global = TenantContext.global(1L, "global", "GLOBAL");

		assertThatThrownBy(() -> policy.assertAssignable(media, 1L, global, ContentScope.GLOBAL, 1L))
				.isInstanceOfSatisfying(BusinessException.class, exception -> org.assertj.core.api.Assertions
						.assertThat(exception.getCode()).isEqualTo("QUESTION_MEDIA_SCOPE_MISMATCH"));
	}

	@Test
	void preventsMediaFromAnotherOrganizationFromBeingAttached() {
		QuestionMediaJpaEntity media = media(ContentScope.ORGANIZATION, 20L, 7L);
		when(questions.findAllUsingMedia(90L)).thenReturn(List.of());

		assertThatThrownBy(() -> policy.assertAssignable(media, 8L,
				TenantContext.organization(30L, "org-30", "ORG30", false), ContentScope.ORGANIZATION, 30L))
				.isInstanceOfSatisfying(BusinessException.class, exception -> org.assertj.core.api.Assertions
						.assertThat(exception.getCode()).isEqualTo("QUESTION_MEDIA_NOT_FOUND"));
	}

	@Test
	void rejectsMissingAuthenticatedContext() {
		QuestionMediaJpaEntity media = media(ContentScope.ORGANIZATION, 20L, 7L);

		assertThatThrownBy(
				() -> policy.assertAccessible(media, null, TenantContext.organization(20L, "org-20", "ORG20", false)))
				.isInstanceOf(BusinessException.class);
	}

	private QuestionMediaJpaEntity media(ContentScope scope, Long organizationId, Long createdBy) {
		QuestionMediaJpaEntity media = QuestionMediaJpaEntity.create("83c62888-2ca1-4d85-a0f6-84ce632f6768",
				"questions/media.png", "media.png", "image/png", 4L, "checksum", scope, organizationId, createdBy,
				Instant.EPOCH);
		setId(media, 90L);
		return media;
	}

	private void setId(QuestionMediaJpaEntity media, Long id) {
		try {
			Field field = QuestionMediaJpaEntity.class.getDeclaredField("id");
			field.setAccessible(true);
			field.set(media, id);
		} catch (ReflectiveOperationException exception) {
			throw new AssertionError(exception);
		}
	}
}
