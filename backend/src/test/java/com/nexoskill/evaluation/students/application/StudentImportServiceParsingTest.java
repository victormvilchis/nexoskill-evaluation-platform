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
}
