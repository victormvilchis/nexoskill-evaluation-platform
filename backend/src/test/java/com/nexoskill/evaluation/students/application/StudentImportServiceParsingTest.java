package com.nexoskill.evaluation.students.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
