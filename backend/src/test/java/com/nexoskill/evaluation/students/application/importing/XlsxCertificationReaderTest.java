package com.nexoskill.evaluation.students.application.importing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nexoskill.evaluation.shared.domain.BusinessException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class XlsxCertificationReaderTest {
    private final XlsxCertificationReader reader = new XlsxCertificationReader();

    @Test
    void readsFirstSheetRegardlessOfNameAndIgnoresLaterSheets() throws Exception {
        byte[] workbook = workbook(
                new SheetDefinition("RESUMEN", Map.of(
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
                        5, "Analista Programador")),
                validSheet("CERTIFICACIONES", "Persona de la segunda hoja"));

        XlsxCertificationReader.SheetData result = reader.read(new ByteArrayInputStream(workbook));

        assertEquals("RESUMEN", result.sheetName());
        assertEquals(1, result.rows().size());
        assertEquals("María López", result.rows().getFirst().value("NOMBRE EXTERNO"));
        assertEquals("APX", result.rows().getFirst().value("TECNOLOGÍA EN LA QUE SE CERTIFICA"));
        assertEquals("Ignorar", result.rows().getFirst().value("COLUMNA ADICIONAL"));
    }

    @Test
    void validatesRequiredHeadersOnlyOnTheFirstSheet() throws Exception {
        byte[] workbook = workbook(
                new SheetDefinition("DATOS", Map.of(
                        0, "NOMBRE EXTERNO",
                        1, "PERFIL",
                        2, "FECHA DE ALTA"), Map.of(
                        0, "Víctor Vilchis",
                        1, "Analista",
                        2, "2026-07-01")),
                validSheet("CERTIFICACIONES", "Persona de la segunda hoja"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> reader.read(new ByteArrayInputStream(workbook)));

        assertEquals("STUDENT_IMPORT_HEADERS_MISSING", exception.getCode());
        assertTrue(exception.getMessage().contains("TECNOLOGÍA EN LA QUE SE CERTIFICA"));
        assertTrue(exception.getMessage().contains("PERFIL TECNOLOGICO"));
    }

    @Test
    void readsASingleSheetWithoutDependingOnItsName() throws Exception {
        byte[] workbook = workbook(validSheet("TABLERO", "Primera Persona"));

        XlsxCertificationReader.SheetData result = reader.read(new ByteArrayInputStream(workbook));

        assertEquals("TABLERO", result.sheetName());
        assertEquals("Primera Persona", result.rows().getFirst().value("NOMBRE EXTERNO"));
    }

    @Test
    void rejectsWorkbookWithoutSheets() throws Exception {
        byte[] workbook = workbook();

        BusinessException exception = assertThrows(BusinessException.class,
                () -> reader.read(new ByteArrayInputStream(workbook)));

        assertEquals("STUDENT_IMPORT_SHEET_REQUIRED", exception.getCode());
    }

    @Test
    void ignoresRowsWithoutExternalNameEvenWhenOtherCellsContainResidualValues() throws Exception {
        Map<Integer, String> headers = Map.of(
                0, "NOMBRE EXTERNO",
                1, "PERFIL",
                2, "FECHA DE ALTA",
                3, "TECNOLOGÍA EN LA QUE SE CERTIFICA",
                4, "PERFIL TECNOLOGICO");
        byte[] workbook = workbookWithRows("CONTROL", headers, List.of(
                Map.of(0, "Primera Persona", 1, "Analista", 3, "Java"),
                Map.of(1, "Formato residual", 3, "Valor aislado"),
                Map.of(0, "   ", 1, "Celda utilizada anteriormente"),
                Map.of(0, "Segunda Persona", 1, "Desarrollador", 3, "APX")));

        XlsxCertificationReader.SheetData result = reader.read(new ByteArrayInputStream(workbook));

        assertEquals(2, result.rows().size());
        assertEquals("Primera Persona", result.rows().get(0).value("NOMBRE EXTERNO"));
        assertEquals("Segunda Persona", result.rows().get(1).value("NOMBRE EXTERNO"));
        assertEquals(2, result.rows().get(0).rowNumber());
        assertEquals(5, result.rows().get(1).rowNumber());
    }

    private SheetDefinition validSheet(String name, String collaborator) {
        return new SheetDefinition(name, Map.of(
                0, "NOMBRE EXTERNO",
                1, "PERFIL",
                2, "FECHA DE ALTA",
                3, "TECNOLOGÍA EN LA QUE SE CERTIFICA",
                4, "PERFIL TECNOLOGICO"), Map.of(
                0, collaborator,
                1, "Analista Programador",
                2, "2026-07-01",
                3, "Java",
                4, "DESARROLLADOR"));
    }

    private byte[] workbook(SheetDefinition... sheets) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            StringBuilder workbookSheets = new StringBuilder();
            StringBuilder relationships = new StringBuilder();
            for (int index = 0; index < sheets.length; index++) {
                int number = index + 1;
                workbookSheets.append("<sheet name=\"").append(escape(sheets[index].name()))
                        .append("\" sheetId=\"").append(number).append("\" r:id=\"rId")
                        .append(number).append("\"/>");
                relationships.append("<Relationship Id=\"rId").append(number)
                        .append("\" Target=\"worksheets/sheet").append(number)
                        .append(".xml\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\"/>");
                put(zip, "xl/worksheets/sheet" + number + ".xml",
                        sheet(sheets[index].headers(), sheets[index].values()));
            }
            put(zip, "xl/workbook.xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                              xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                      <sheets>%s</sheets>
                    </workbook>
                    """.formatted(workbookSheets));
            put(zip, "xl/_rels/workbook.xml.rels", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      %s
                    </Relationships>
                    """.formatted(relationships));
        }
        return bytes.toByteArray();
    }

    private byte[] workbookWithRows(String name, Map<Integer, String> headers,
            List<Map<Integer, String>> rows) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            put(zip, "xl/workbook.xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                              xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                      <sheets><sheet name="%s" sheetId="1" r:id="rId1"/></sheets>
                    </workbook>
                    """.formatted(escape(name)));
            put(zip, "xl/_rels/workbook.xml.rels", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      <Relationship Id="rId1" Target="worksheets/sheet1.xml"
                                    Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet"/>
                    </Relationships>
                    """);
            put(zip, "xl/worksheets/sheet1.xml", sheet(headers, rows));
        }
        return bytes.toByteArray();
    }

    private String sheet(Map<Integer, String> headers, Map<Integer, String> values) {
        return sheet(headers, List.of(values));
    }

    private String sheet(Map<Integer, String> headers, List<Map<Integer, String>> values) {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                .append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>")
                .append(row(1, headers));
        for (int index = 0; index < values.size(); index++) {
            xml.append(row(index + 2, values.get(index)));
        }
        return xml.append("</sheetData></worksheet>").toString();
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

    private record SheetDefinition(String name, Map<Integer, String> headers, Map<Integer, String> values) {}
}
