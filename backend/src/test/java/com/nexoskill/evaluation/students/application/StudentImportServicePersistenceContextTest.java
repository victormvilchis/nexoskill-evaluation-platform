package com.nexoskill.evaluation.students.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexoskill.evaluation.certifications.application.StudentCertificationService;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import com.nexoskill.evaluation.students.application.importing.XlsxCertificationReader;
import com.nexoskill.evaluation.students.infrastructure.persistence.StudentImportReceiptStore;
import com.nexoskill.evaluation.students.infrastructure.persistence.StudentJpaEntity;
import jakarta.persistence.EntityManager;
import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.transaction.PlatformTransactionManager;

class StudentImportServicePersistenceContextTest {

	@Test
	void reappliesAndVerifiesTheImportedAdmissionDateBeforeCertifications() {
		EntityManager entityManager = mock(EntityManager.class);
		NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
		StudentJpaEntity student = mock(StudentJpaEntity.class);
		LocalDate admissionDate = LocalDate.of(2015, 10, 2);
		when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(1);
		when(entityManager.find(StudentJpaEntity.class, 91L)).thenReturn(student);
		when(student.getOrganizationId()).thenReturn(8L);
		when(student.getPublicId()).thenReturn("student-public-id");
		when(student.getAdmissionDate()).thenReturn(admissionDate);
		StudentImportService service = service(jdbc, entityManager);

		service.synchronizeStudentFoundationForCertification(91L, 8L, "student-public-id", admissionDate);

		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<SqlParameterSource> parameters = ArgumentCaptor.forClass(SqlParameterSource.class);
		InOrder order = inOrder(entityManager, jdbc);
		order.verify(entityManager).flush();
		order.verify(jdbc).update(sql.capture(), parameters.capture());
		order.verify(entityManager).clear();
		order.verify(entityManager).find(StudentJpaEntity.class, 91L);
		order.verify(entityManager).refresh(student);

		assertTrue(sql.getValue().contains("ADMISSION_DATE = :admissionDate"));
		assertFalse(sql.getValue().contains("CERTIFICATION_ENROLLMENT_DATE"));
		assertEquals(Date.valueOf(admissionDate), parameters.getValue().getValue("admissionDate"));
		assertEquals(91L, parameters.getValue().getValue("studentId"));
		assertEquals(8L, parameters.getValue().getValue("organizationId"));
		assertEquals("student-public-id", parameters.getValue().getValue("studentPublicId"));
	}

	@Test
	void stopsTheRowWhenTheFoundationCannotBeUpdatedWithinTheOrganization() {
		EntityManager entityManager = mock(EntityManager.class);
		NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
		when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(0);
		StudentImportService service = service(jdbc, entityManager);

		BusinessException exception = assertThrows(BusinessException.class, () -> service
				.synchronizeStudentFoundationForCertification(91L, 8L, "missing-student", LocalDate.of(2026, 7, 1)));

		assertEquals("STUDENT_IMPORT_FOUNDATION_SYNC_FAILED", exception.getCode());
		verify(entityManager).flush();
	}

	@Test
	void stopsTheRowWhenJpaStillCannotObserveTheImportedAdmissionDate() {
		EntityManager entityManager = mock(EntityManager.class);
		NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
		StudentJpaEntity student = mock(StudentJpaEntity.class);
		when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(1);
		when(entityManager.find(StudentJpaEntity.class, 91L)).thenReturn(student);
		when(student.getOrganizationId()).thenReturn(8L);
		when(student.getPublicId()).thenReturn("student-public-id");
		when(student.getAdmissionDate()).thenReturn(null);
		StudentImportService service = service(jdbc, entityManager);

		BusinessException exception = assertThrows(BusinessException.class,
				() -> service.synchronizeStudentFoundationForCertification(91L, 8L, "student-public-id",
						LocalDate.of(2007, 12, 18)));

		assertEquals("STUDENT_IMPORT_FOUNDATION_SYNC_FAILED", exception.getCode());
		verify(entityManager).refresh(student);
	}

	@Test
	void acceptsCertificationApplicationWithoutAnImportedAdmissionDate() {
		EntityManager entityManager = mock(EntityManager.class);
		NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
		StudentJpaEntity student = mock(StudentJpaEntity.class);
		when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(1);
		when(entityManager.find(StudentJpaEntity.class, 91L)).thenReturn(student);
		when(student.getOrganizationId()).thenReturn(8L);
		when(student.getPublicId()).thenReturn("student-public-id");
		when(student.getAdmissionDate()).thenReturn(null);
		StudentImportService service = service(jdbc, entityManager);

		service.synchronizeStudentFoundationForCertification(91L, 8L, "student-public-id", null);

		verify(entityManager).refresh(student);
	}

	private StudentImportService service(NamedParameterJdbcTemplate jdbc, EntityManager entityManager) {
		return new StudentImportService(mock(XlsxCertificationReader.class), mock(StudentFoundationService.class),
				mock(StudentService.class), mock(StudentExperienceService.class),
				mock(StudentCertificationService.class), jdbc, Clock.systemUTC(),
				mock(PlatformTransactionManager.class), entityManager, mock(StudentImportReceiptStore.class));
	}
}
