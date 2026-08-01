package com.nexoskill.evaluation.students.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class StudentImportServiceParsingTest {
    @Test
    void acceptsSpreadsheetApplicabilityVariants() {
        assertTrue(StudentImportService.parseImportBoolean("SI"));
        assertTrue(StudentImportService.parseImportBoolean("Sí aplica"));
        assertFalse(StudentImportService.parseImportBoolean("NO"));
        assertFalse(StudentImportService.parseImportBoolean("NO AGILE"));
        assertFalse(StudentImportService.parseImportBoolean("No aplica"));
        assertFalse(StudentImportService.parseImportBoolean(""));
    }

    @Test
    void rejectsUnknownApplicabilityValues() {
        assertNull(StudentImportService.parseImportBoolean("POR REVISAR"));
    }

    @Test
    void treatsNoAplicaAsEmptyTrackingStatus() {
        assertFalse(StudentImportService.hasMeaningfulImportStatus("No aplica"));
        assertFalse(StudentImportService.hasMeaningfulImportStatus("N/A"));
        assertFalse(StudentImportService.hasMeaningfulImportStatus(""));
        assertTrue(StudentImportService.hasMeaningfulImportStatus("Vigente"));
        assertTrue(StudentImportService.hasMeaningfulImportStatus("Aprobado"));
    }
    @Test
    void ignoresEmptyTrackingWhenCertificationDoesNotApply() {
        assertFalse(StudentImportService.hasImportTrackingData(
                "No aplica", null, null, null, null));
        assertFalse(StudentImportService.hasImportTrackingData(
                "No aplica", "No aplica", null, null, 0));
        assertTrue(StudentImportService.hasImportTrackingData(
                null, "Aprobado", null, null, null));
        assertTrue(StudentImportService.hasImportTrackingData(
                null, null, LocalDate.of(2026, 7, 1), null, null));
    }
    @Test
    void keepsTheExcelDeadlineWhenItDiffersFromTheCalculatedDate() {
        LocalDate imported = LocalDate.of(2025, 12, 15);
        LocalDate calculated = LocalDate.of(2008, 2, 18);

        assertEquals(imported, StudentImportService.resolveImportDeadline(imported, calculated));
        assertEquals(calculated, StudentImportService.resolveImportDeadline(null, calculated));
    }

    @Test
    void parsesTheExactExcelSerialDatesFromTheReportedWorkbook() {
        assertEquals(LocalDate.of(2007, 12, 18), StudentImportService.parseImportDateValue("39434"));
        assertEquals(LocalDate.of(2015, 10, 2), StudentImportService.parseImportDateValue("42279"));
        assertEquals(LocalDate.of(2004, 12, 20), StudentImportService.parseImportDateValue("38341"));
    }

    @Test
    void parsesTextDatesUsedByTheLayout() {
        assertEquals(LocalDate.of(2007, 12, 18), StudentImportService.parseImportDateValue("18/12/2007"));
        assertEquals(LocalDate.of(2015, 10, 2), StudentImportService.parseImportDateValue("02/10/2015"));
        assertEquals(LocalDate.of(2004, 12, 20), StudentImportService.parseImportDateValue("2004-12-20"));
        assertNull(StudentImportService.parseImportDateValue("fecha pendiente"));
    }
    @Test
    void validatesApplicationDatesBeforeApplyingTheImport() {
        assertFalse(StudentImportService.requiresImportApplicationDate(
                "ONE", true, true, "Aprobada"));
        assertFalse(StudentImportService.requiresImportApplicationDate(
                "AGILE", true, true, "Aprobada"));
        assertTrue(StudentImportService.requiresImportApplicationDate(
                "DEVELOPMENT_SECURITY", true, true, "Vigente — Regular"));
        assertTrue(StudentImportService.requiresImportApplicationDate(
                "TECHNOLOGICAL", true, true, "Aprobada"));
        assertTrue(StudentImportService.requiresImportApplicationDate(
                "NORMATIVE_TESTING", true, null, "Vigente — Regular"));
        assertFalse(StudentImportService.requiresImportApplicationDate(
                "NORMATIVE_TESTING", false, true, "Aprobada"));
        assertFalse(StudentImportService.requiresImportApplicationDate(
                "TECHNOLOGICAL", true, null, "Pendiente"));
    }
    @Test
    void resolvesMissingAttemptsIdempotently() {
        assertEquals(1, StudentImportService.resolveInferredAttempt(null, false));
        assertEquals(1, StudentImportService.resolveInferredAttempt(0, false));
        assertEquals(1, StudentImportService.resolveInferredAttempt(1, true));
        assertEquals(2, StudentImportService.resolveInferredAttempt(1, false));
        assertEquals(3, StudentImportService.resolveInferredAttempt(2, false));
        assertEquals(2, StudentImportService.resolveInferredAttempt(2, true));
    }

    @Test
    void normalizesCertificationStatusesUsedByTheLargeWorkbook() {
        assertEquals("Vigente — Regular",
                StudentImportService.normalizeImportCertificationStatus("DEVELOPMENT_SECURITY", "VIGENTE - REGULAR"));
        assertEquals("Vigente — Próxima a vencer",
                StudentImportService.normalizeImportCertificationStatus("TECHNOLOGICAL", "VIGENTE - PROXIMO A VENCER"));
        assertEquals("Sin presentar — Próxima a vencer",
                StudentImportService.normalizeImportCertificationStatus(
                        "NORMATIVE_TESTING", "SIN PRESENTAR - PROXIMO A VENCER"));
        assertEquals("Vencida",
                StudentImportService.normalizeImportCertificationStatus(
                        "DEVELOPMENT_SECURITY", "SIN PRESENTAR - FUERA DE NORMA"));
        assertEquals("Vencida",
                StudentImportService.normalizeImportCertificationStatus(
                        "TECHNOLOGICAL", "VENCIDO - MENOR A DOS MESES"));
        assertEquals("Aprobada",
                StudentImportService.normalizeImportCertificationStatus("ONE", "Aprobado"));
        assertEquals("Aprobada",
                StudentImportService.normalizeImportCertificationStatus("AGILE", "SI"));
        assertEquals("Sin presentar",
                StudentImportService.normalizeImportCertificationStatus("AGILE", "EN TIEMPO"));
        assertEquals("No aplica",
                StudentImportService.normalizeImportCertificationStatus("TECHNOLOGICAL", "NO APLICA"));
    }

    @Test
    void normalizesExamStatusesUsedByTheLargeWorkbook() {
        assertEquals("Aprobado", StudentImportService.normalizeImportExamStatus("APROBADO"));
        assertEquals("No aprobado", StudentImportService.normalizeImportExamStatus("REPROBADO"));
        assertEquals("Sin presentar", StudentImportService.normalizeImportExamStatus("SIN EXAMEN"));
        assertNull(StudentImportService.normalizeImportExamStatus("NO APLICA"));
    }

    @Test
    void comparesAttemptsOnlyWhenTheSpreadsheetContainsAnAttemptColumn() {
        assertTrue(StudentImportService.importsAttempt("TECHNOLOGICAL"));
        assertTrue(StudentImportService.importsAttempt("DEVELOPMENT_SECURITY"));
        assertFalse(StudentImportService.importsAttempt("NORMATIVE_TESTING"));
        assertFalse(StudentImportService.importsAttempt("ONE"));
        assertFalse(StudentImportService.importsAttempt("AGILE"));
    }

}
