package com.nexoskill.evaluation.catalogs.application;

import com.nexoskill.evaluation.catalogs.domain.CatalogType;
import java.util.List;

/**
 * Relaciones persistidas que impiden eliminar físicamente un valor de catálogo.
 *
 * Cada consulta corresponde a una relación real del esquema vigente. Mantener el
 * nombre funcional junto al SQL permite mostrar un total consistente y detallar
 * qué tipo de registros conserva la referencia.
 */
final class CatalogDependencyQueries {
    private CatalogDependencyQueries() {
    }

    static List<DependencyQuery> forType(CatalogType type) {
        return switch (type) {
        case CATEGORIES -> List.of(
                query("preguntas con categoría principal",
                        "SELECT COUNT(*) FROM QUESTION WHERE CATEGORY_ID = :key"),
                query("asignaciones adicionales de preguntas",
                        "SELECT COUNT(*) FROM QUESTION_CATEGORY_RELATION WHERE CATEGORY_ID = :key"),
                query("colecciones relacionadas",
                        "SELECT COUNT(*) FROM COLLECTION_CATEGORY_RELATION WHERE CATEGORY_ID = :key"),
                query("secciones de formularios relacionadas",
                        "SELECT COUNT(*) FROM FORM_QUESTION_POOL WHERE CATEGORY_ID = :key"),
                query("copias organizacionales derivadas",
                        "SELECT COUNT(*) FROM QUESTION_CATEGORY WHERE SOURCE_GLOBAL_ID = :key"));
        case TECHNOLOGIES -> List.of(
                query("preguntas relacionadas",
                        "SELECT COUNT(*) FROM QUESTION WHERE TECHNOLOGY_ID = :key"),
                query("colaboradores clasificados por tecnología",
                        "SELECT COUNT(*) FROM STUDENT WHERE TECHNOLOGY_ID = :key"),
                query("talentos clasificados por tecnología",
                        "SELECT COUNT(*) FROM STUDENT WHERE TALENT_TECHNOLOGY_ID = :key"),
                query("ciclos de certificación tecnológica",
                        "SELECT COUNT(*) FROM STUDENT_CERTIFICATION_CYCLE WHERE TECHNOLOGY_ID = :key"),
                query("perfiles de certificación relacionados",
                        "SELECT COUNT(*) FROM STUDENT_CERTIFICATION_PROFILE profile "
                                + "JOIN CERTIFICATION_TECHNOLOGY_CATALOG certification "
                                + "ON certification.CERTIFICATION_TECHNOLOGY_ID = profile.CERTIFICATION_TECHNOLOGY_ID "
                                + "WHERE certification.MASTER_TECHNOLOGY_ID = :key"));
        case PROFESSIONAL_PROFILES -> List.of(
                query("colaboradores con el perfil",
                        "SELECT COUNT(*) FROM STUDENT WHERE PROFESSIONAL_PROFILE_ID = :key"),
                query("configuraciones de certificación con el perfil",
                        "SELECT COUNT(*) FROM STUDENT_CERTIFICATION_PROFILE WHERE PROFESSIONAL_PROFILE_ID = :key"));
        case TECHNOLOGICAL_PROFILES -> List.of(
                query("colaboradores con el perfil tecnológico",
                        "SELECT COUNT(*) FROM STUDENT WHERE TECHNOLOGICAL_PROFILE_ID = :key"),
                query("configuraciones de certificación con el perfil tecnológico",
                        "SELECT COUNT(*) FROM STUDENT_CERTIFICATION_PROFILE WHERE TECHNOLOGICAL_PROFILE = "
                                + "(SELECT PROFILE_CODE FROM TECHNOLOGICAL_PROFILE_CATALOG "
                                + "WHERE TECHNOLOGICAL_PROFILE_ID = :key)"),
                query("perfiles profesionales que lo sugieren",
                        "SELECT COUNT(*) FROM CERTIFICATION_PROFILE_CATALOG WHERE SUGGESTED_TECH_PROFILE = "
                                + "(SELECT PROFILE_CODE FROM TECHNOLOGICAL_PROFILE_CATALOG "
                                + "WHERE TECHNOLOGICAL_PROFILE_ID = :key)"));
        case QUESTION_TYPES -> List.of(
                query("preguntas relacionadas",
                        "SELECT COUNT(*) FROM QUESTION WHERE TYPE_CODE = :key"));
        case DIFFICULTIES -> List.of(
                query("preguntas relacionadas",
                        "SELECT COUNT(*) FROM QUESTION WHERE DIFFICULTY_CODE = :key"));
        };
    }

    static String deleteTechnologyCertificationLinksSql() {
        return "DELETE FROM CERTIFICATION_TECHNOLOGY_CATALOG WHERE MASTER_TECHNOLOGY_ID = :key";
    }

    static String deleteTechnologySql() {
        return "DELETE FROM QUESTION_TECHNOLOGY "
                + "WHERE TECHNOLOGY_ID = :key AND VERSION_NO = :version";
    }

    private static DependencyQuery query(String label, String sql) {
        return new DependencyQuery(label, sql);
    }

    record DependencyQuery(String label, String sql) {
    }
}
