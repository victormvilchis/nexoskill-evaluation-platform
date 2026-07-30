package com.nexoskill.evaluation.students.application.importing;

import com.nexoskill.evaluation.shared.domain.BusinessException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.springframework.stereotype.Component;

/**
 * Lector XLSX acotado al flujo de importación de colaboradores. No ejecuta fórmulas,
 * no procesa macros y utiliza exclusivamente la primera hoja del libro.
 */
@Component
public class XlsxCertificationReader {
    static final long MAX_UNCOMPRESSED_BYTES = 30L * 1024L * 1024L;
    static final int MAX_ROWS = 10_000;
    static final int MAX_COLUMNS = 256;
    static final int MAX_SHARED_STRINGS = 200_000;
    private static final Set<String> REQUIRED_HEADERS = Set.of(
            normalizeHeader("NOMBRE EXTERNO"),
            normalizeHeader("PERFIL"),
            normalizeHeader("FECHA DE ALTA"),
            normalizeHeader("TECNOLOGÍA EN LA QUE SE CERTIFICA"),
            normalizeHeader("PERFIL TECNOLOGICO"));

    public SheetData read(InputStream input) {
        if (input == null) {
            throw new BusinessException("STUDENT_IMPORT_FILE_REQUIRED", "Selecciona un archivo Excel para continuar.");
        }
        try {
            Map<String, byte[]> entries = unzip(input);
            byte[] workbook = requiredEntry(entries, "xl/workbook.xml");
            byte[] relationships = requiredEntry(entries, "xl/_rels/workbook.xml.rels");
            Map<String, String> relationshipTargets = parseRelationships(relationships);
            SheetRef sheet = findFirstSheet(workbook, relationshipTargets);
            List<String> sharedStrings = parseSharedStrings(entries.get("xl/sharedStrings.xml"));
            byte[] sheetBytes = entries.get(sheet.path());
            if (sheetBytes == null) {
                throw new BusinessException("STUDENT_IMPORT_SHEET_INVALID",
                        "La primera hoja del archivo no pudo leerse.");
            }
            return parseSheet(sheet.name(), sheetBytes, sharedStrings);
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException | XMLStreamException exception) {
            throw new BusinessException("STUDENT_IMPORT_FILE_INVALID",
                    "El archivo no es un Excel .xlsx válido o está dañado.");
        }
    }

    private Map<String, byte[]> unzip(InputStream input) throws IOException {
        Map<String, byte[]> entries = new HashMap<>();
        long total = 0;
        try (ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String name = normalizeZipPath(entry.getName());
                if (!isRelevantEntry(name)) continue;
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                int read;
                while ((read = zip.read(buffer)) >= 0) {
                    total += read;
                    if (total > MAX_UNCOMPRESSED_BYTES) {
                        throw new BusinessException("STUDENT_IMPORT_FILE_TOO_LARGE",
                                "El contenido descomprimido del archivo supera el límite permitido.");
                    }
                    output.write(buffer, 0, read);
                }
                entries.put(name, output.toByteArray());
            }
        }
        return entries;
    }

    private boolean isRelevantEntry(String name) {
        return "xl/workbook.xml".equals(name)
                || "xl/_rels/workbook.xml.rels".equals(name)
                || "xl/sharedStrings.xml".equals(name)
                || name.startsWith("xl/worksheets/");
    }

    private static String normalizeZipPath(String value) {
        String normalized = value == null ? "" : value.replace('\\', '/');
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        if (normalized.contains("../")) {
            throw new BusinessException("STUDENT_IMPORT_FILE_INVALID", "El archivo contiene rutas no permitidas.");
        }
        return normalized;
    }

    private static byte[] requiredEntry(Map<String, byte[]> entries, String name) {
        byte[] value = entries.get(name);
        if (value == null) {
            throw new BusinessException("STUDENT_IMPORT_FILE_INVALID", "El archivo no contiene una estructura XLSX válida.");
        }
        return value;
    }

    private Map<String, String> parseRelationships(byte[] xml) throws XMLStreamException {
        Map<String, String> targets = new HashMap<>();
        XMLStreamReader reader = xmlReader(xml);
        try {
            while (reader.hasNext()) {
                if (reader.next() == XMLStreamConstants.START_ELEMENT
                        && "Relationship".equals(reader.getLocalName())) {
                    String id = attribute(reader, "Id");
                    String target = attribute(reader, "Target");
                    if (id != null && target != null) {
                        String path = target.replace('\\', '/');
                        while (path.startsWith("../")) path = path.substring(3);
                        if (path.startsWith("/")) path = path.substring(1);
                        if (!path.startsWith("xl/")) path = "xl/" + path;
                        targets.put(id, normalizeZipPath(path));
                    }
                }
            }
        } finally {
            reader.close();
        }
        return targets;
    }

    private SheetRef findFirstSheet(byte[] xml, Map<String, String> relationships)
            throws XMLStreamException {
        XMLStreamReader reader = xmlReader(xml);
        try {
            while (reader.hasNext()) {
                if (reader.next() == XMLStreamConstants.START_ELEMENT && "sheet".equals(reader.getLocalName())) {
                    String name = attribute(reader, "name");
                    String relationId = attributeByLocalName(reader, "id");
                    String path = relationships.get(relationId);
                    if (name == null || name.isBlank() || path == null || path.isBlank()) {
                        throw new BusinessException("STUDENT_IMPORT_SHEET_INVALID",
                                "La primera hoja del archivo no tiene una referencia válida.");
                    }
                    return new SheetRef(name.trim(), path);
                }
            }
        } finally {
            reader.close();
        }
        throw new BusinessException("STUDENT_IMPORT_SHEET_REQUIRED",
                "El archivo debe contener al menos una hoja.");
    }

    private List<String> parseSharedStrings(byte[] xml) throws XMLStreamException {
        if (xml == null) return List.of();
        List<String> values = new ArrayList<>();
        XMLStreamReader reader = xmlReader(xml);
        StringBuilder current = null;
        try {
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT && "si".equals(reader.getLocalName())) {
                    current = new StringBuilder();
                } else if (event == XMLStreamConstants.START_ELEMENT && "t".equals(reader.getLocalName())
                        && current != null) {
                    current.append(reader.getElementText());
                } else if (event == XMLStreamConstants.END_ELEMENT && "si".equals(reader.getLocalName())) {
                    values.add(current == null ? "" : current.toString());
                    if (values.size() > MAX_SHARED_STRINGS) {
                        throw new BusinessException("STUDENT_IMPORT_FILE_TOO_LARGE",
                                "El archivo contiene demasiados textos compartidos.");
                    }
                    current = null;
                }
            }
        } finally {
            reader.close();
        }
        return List.copyOf(values);
    }

    private SheetData parseSheet(String sheetName, byte[] xml, List<String> sharedStrings)
            throws XMLStreamException {
        List<RawRow> rows = new ArrayList<>();
        XMLStreamReader reader = xmlReader(xml);
        int currentRow = 0;
        Map<Integer, String> cells = null;
        try {
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT && "row".equals(reader.getLocalName())) {
                    currentRow = parseInteger(attribute(reader, "r"), rows.size() + 1);
                    cells = new LinkedHashMap<>();
                } else if (event == XMLStreamConstants.START_ELEMENT && "c".equals(reader.getLocalName())
                        && cells != null) {
                    String reference = attribute(reader, "r");
                    int column = columnIndex(reference);
                    if (column >= 0 && column < MAX_COLUMNS) {
                        String type = attribute(reader, "t");
                        String value = readCell(reader, type, sharedStrings);
                        if (value != null && !value.isBlank()) cells.put(column, value.trim());
                    } else {
                        skipElement(reader, "c");
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT && "row".equals(reader.getLocalName())
                        && cells != null) {
                    if (!cells.isEmpty()) rows.add(new RawRow(currentRow, Map.copyOf(cells)));
                    if (rows.size() > MAX_ROWS + 30) {
                        throw new BusinessException("STUDENT_IMPORT_TOO_MANY_ROWS",
                                "El archivo supera el máximo de " + MAX_ROWS + " filas procesables.");
                    }
                    cells = null;
                }
            }
        } finally {
            reader.close();
        }
        if (rows.isEmpty()) {
            throw new BusinessException("STUDENT_IMPORT_EMPTY", "La primera hoja del archivo no contiene datos.");
        }
        int headerPosition = findHeaderPosition(rows);
        RawRow header = rows.get(headerPosition);
        Map<String, HeaderRef> headers = new LinkedHashMap<>();
        header.cells().forEach((column, original) -> {
            String normalized = normalizeHeader(original);
            if (!normalized.isBlank() && !headers.containsKey(normalized)) {
                headers.put(normalized, new HeaderRef(column, original));
            }
        });
        List<String> missing = REQUIRED_HEADERS.stream()
                .filter(required -> !headers.containsKey(required))
                .map(XlsxCertificationReader::displayRequiredHeader)
                .sorted()
                .toList();
        if (!missing.isEmpty()) {
            throw new BusinessException("STUDENT_IMPORT_HEADERS_MISSING",
                    "No se encontraron las columnas obligatorias: " + String.join(", ", missing) + ".",
                    Map.of("file", "Faltan columnas obligatorias en la primera hoja del archivo."));
        }
        List<RowData> data = new ArrayList<>();
        for (int index = headerPosition + 1; index < rows.size(); index++) {
            RawRow row = rows.get(index);
            Map<String, String> values = new LinkedHashMap<>();
            headers.forEach((normalized, ref) -> values.put(normalized,
                    row.cells().getOrDefault(ref.column(), "").trim()));
            if (values.values().stream().allMatch(String::isBlank)) continue;
            data.add(new RowData(row.number(), Collections.unmodifiableMap(values)));
            if (data.size() > MAX_ROWS) {
                throw new BusinessException("STUDENT_IMPORT_TOO_MANY_ROWS",
                        "El archivo supera el máximo de " + MAX_ROWS + " filas procesables.");
            }
        }
        if (data.isEmpty()) {
            throw new BusinessException("STUDENT_IMPORT_EMPTY",
                    "La primera hoja del archivo no contiene colaboradores debajo de los encabezados.");
        }
        Map<String, String> originalHeaders = new LinkedHashMap<>();
        headers.forEach((normalized, ref) -> originalHeaders.put(normalized, ref.original()));
        return new SheetData(sheetName, header.number(), Map.copyOf(originalHeaders), List.copyOf(data));
    }

    private int findHeaderPosition(List<RawRow> rows) {
        int limit = Math.min(rows.size(), 25);
        int bestIndex = -1;
        int bestMatches = 0;
        for (int index = 0; index < limit; index++) {
            Set<String> normalized = rows.get(index).cells().values().stream()
                    .map(XlsxCertificationReader::normalizeHeader)
                    .collect(java.util.stream.Collectors.toSet());
            int matches = (int) REQUIRED_HEADERS.stream().filter(normalized::contains).count();
            if (matches > bestMatches) {
                bestMatches = matches;
                bestIndex = index;
            }
            if (matches == REQUIRED_HEADERS.size()) return index;
        }
        if (bestIndex >= 0 && bestMatches >= 3) return bestIndex;
        throw new BusinessException("STUDENT_IMPORT_HEADERS_NOT_FOUND",
                "No fue posible localizar la fila de encabezados en la primera hoja del archivo.");
    }

    private String readCell(XMLStreamReader reader, String type, List<String> sharedStrings)
            throws XMLStreamException {
        String value = null;
        StringBuilder inline = null;
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT && "v".equals(reader.getLocalName())) {
                value = reader.getElementText();
            } else if (event == XMLStreamConstants.START_ELEMENT && "is".equals(reader.getLocalName())) {
                inline = new StringBuilder();
            } else if (event == XMLStreamConstants.START_ELEMENT && "t".equals(reader.getLocalName())
                    && inline != null) {
                inline.append(reader.getElementText());
            } else if (event == XMLStreamConstants.END_ELEMENT && "c".equals(reader.getLocalName())) {
                break;
            }
        }
        if (inline != null) return inline.toString();
        if (value == null) return "";
        if ("s".equals(type)) {
            int index = parseInteger(value, -1);
            return index >= 0 && index < sharedStrings.size() ? sharedStrings.get(index) : "";
        }
        if ("b".equals(type)) return "1".equals(value) ? "SI" : "NO";
        return value;
    }

    private static void skipElement(XMLStreamReader reader, String localName) throws XMLStreamException {
        int depth = 1;
        while (reader.hasNext() && depth > 0) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT && localName.equals(reader.getLocalName())) depth++;
            if (event == XMLStreamConstants.END_ELEMENT && localName.equals(reader.getLocalName())) depth--;
        }
    }

    private static int columnIndex(String reference) {
        if (reference == null || reference.isBlank()) return -1;
        int result = 0;
        int letters = 0;
        for (int i = 0; i < reference.length(); i++) {
            char character = reference.charAt(i);
            if (!Character.isLetter(character)) break;
            result = result * 26 + (Character.toUpperCase(character) - 'A' + 1);
            letters++;
        }
        return letters == 0 ? -1 : result - 1;
    }

    private static XMLStreamReader xmlReader(byte[] xml) throws XMLStreamException {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
        return factory.createXMLStreamReader(new ByteArrayInputStream(xml));
    }

    private static String attribute(XMLStreamReader reader, String name) {
        for (int i = 0; i < reader.getAttributeCount(); i++) {
            if (name.equals(reader.getAttributeName(i).toString())) return reader.getAttributeValue(i);
        }
        return null;
    }

    private static String attributeByLocalName(XMLStreamReader reader, String name) {
        for (int i = 0; i < reader.getAttributeCount(); i++) {
            if (name.equals(reader.getAttributeLocalName(i))) return reader.getAttributeValue(i);
        }
        return null;
    }

    private static int parseInteger(String value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    public static String normalizeHeader(String value) {
        if (value == null) return "";
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toUpperCase(Locale.ROOT)
                .replace('¿', ' ')
                .replace('?', ' ');
        return decomposed.replaceAll("[^A-Z0-9]+", " ").trim().replaceAll("\\s+", " ");
    }

    private static String displayRequiredHeader(String normalized) {
        return switch (normalized) {
            case "NOMBRE EXTERNO" -> "NOMBRE EXTERNO";
            case "PERFIL" -> "PERFIL";
            case "FECHA DE ALTA" -> "FECHA DE ALTA";
            case "TECNOLOGIA EN LA QUE SE CERTIFICA" -> "TECNOLOGÍA EN LA QUE SE CERTIFICA";
            case "PERFIL TECNOLOGICO" -> "PERFIL TECNOLOGICO";
            default -> normalized;
        };
    }

    private record SheetRef(String name, String path) {}
    private record RawRow(int number, Map<Integer, String> cells) {}
    private record HeaderRef(int column, String original) {}

    public record RowData(int rowNumber, Map<String, String> values) {
        public String value(String header) {
            return values.getOrDefault(normalizeHeader(header), "");
        }
    }

    public record SheetData(String sheetName, int headerRow, Map<String, String> headers,
                            List<RowData> rows) {}
}
