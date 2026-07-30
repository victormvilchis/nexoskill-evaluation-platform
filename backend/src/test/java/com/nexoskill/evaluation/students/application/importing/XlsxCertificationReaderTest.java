package com.nexoskill.evaluation.students.application.importing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nexoskill.evaluation.shared.domain.BusinessException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class XlsxCertificationReaderTest {
    private final XlsxCertificationReader reader = new XlsxCertificationReader();

    @Test
    void readsCertificationSheetIgnoringCaseOrderAndExtraColumns() throws Exception {
        byte[] workbook = workbook("  certificaciones.  ", Map.of(
                0, "PERFIL TECNOLOGICO",
                1, "COLUMNA ADICIONAL",
                2, "NOMBRE EXTERNO",
                3, "TECNOLOGÍA EN LA QUE SE CERTIFICA",
                4, "FECHA DE ALTA",
                5, "PERFIL"), Map.of(
                0, "DESARROLLADOR",
                1, "Ignorar",
                2, "María López",
                3, "APX",
                4, "45800",
                5, "Analista Programador"));

        XlsxCertificationReader.SheetData result = reader.read(new ByteArrayInputStream(workbook));

        assertEquals("certificaciones.", result.sheetName());
        assertEquals(1, result.rows().size());
        assertEquals("María López", result.rows().getFirst().value("NOMBRE EXTERNO"));
        assertEquals("APX", result.rows().getFirst().value("TECNOLOGÍA EN LA QUE SE CERTIFICA"));
        assertEquals("Ignorar", result.rows().getFirst().value("COLUMNA ADICIONAL"));
    }

    @Test
    void reportsAllMissingRequiredHeaders() throws Exception {
        byte[] workbook = workbook("CERTIFICACIONES", Map.of(
                0, "NOMBRE EXTERNO",
                1, "PERFIL",
                2, "FECHA DE ALTA"), Map.of(
                0, "Víctor Vilchis",
                1, "Analista",
                2, "2026-07-01"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> reader.read(new ByteArrayInputStream(workbook)));

        assertEquals("STUDENT_IMPORT_HEADERS_MISSING", exception.getCode());
        assertTrue(exception.getMessage().contains("TECNOLOGÍA EN LA QUE SE CERTIFICA"));
        assertTrue(exception.getMessage().contains("PERFIL TECNOLOGICO"));
    }

    @Test
    void rejectsWorkbookWithoutCertificationSheet() throws Exception {
        byte[] workbook = workbook("TABLERO", Map.of(
                0, "NOMBRE EXTERNO",
                1, "PERFIL",
                2, "FECHA DE ALTA",
                3, "TECNOLOGÍA EN LA QUE SE CERTIFICA",
                4, "PERFIL TECNOLOGICO"), Map.of(
                0, "Víctor Vilchis",
                1, "Analista",
                2, "2026-07-01",
                3, "Java",
                4, "DESARROLLADOR"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> reader.read(new ByteArrayInputStream(workbook)));

        assertEquals("STUDENT_IMPORT_SHEET_REQUIRED", exception.getCode());
        assertTrue(exception.getMessage().contains("CERTIFICACIONES"));
    }

    private byte[] workbook(String sheetName, Map<Integer, String> headers, Map<Integer, String> values)
            throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            put(zip, "xl/workbook.xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                              xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                      <sheets><sheet name="%s" sheetId="1" r:id="rId1"/></sheets>
                    </workbook>
                    """.formatted(escape(sheetName)));
            put(zip, "xl/_rels/workbook.xml.rels", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      <Relationship Id="rId1" Target="worksheets/sheet1.xml"
                        Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet"/>
                    </Relationships>
                    """);
            put(zip, "xl/worksheets/sheet1.xml", sheet(headers, values));
        }
        return bytes.toByteArray();
    }

    private String sheet(Map<Integer, String> headers, Map<Integer, String> values) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>"
                + row(1, headers) + row(2, values) + "</sheetData></worksheet>";
    }

    private String row(int number, Map<Integer, String> values) {
        StringBuilder xml = new StringBuilder("<row r=\"").append(number).append("\">");
        new LinkedHashMap<>(values).entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
                xml.append("<c r=\"").append(column(entry.getKey())).append(number)
                        .append("\" t=\"inlineStr\"><is><t>")
                        .append(escape(entry.getValue())).append("</t></is></c>"));
        return xml.append("</row>").toString();
    }

    private String column(int index) {
        StringBuilder value = new StringBuilder();
        int current = index + 1;
        while (current > 0) {
            current--;
            value.insert(0, (char) ('A' + current % 26));
            current /= 26;
        }
        return value.toString();
    }

    private void put(ZipOutputStream zip, String name, String content) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
