package com.nexoskill.evaluation.catalogs.domain;

public enum CatalogType {
    CATEGORIES("Categorías", "Clasificación de preguntas globales y organizacionales", true),
    TECHNOLOGIES("Tecnologías", "Tecnologías reutilizadas por contenido y certificaciones", true),
    PROFESSIONAL_PROFILES("Perfiles", "Perfiles profesionales utilizados en certificaciones", true),
    TECHNOLOGICAL_PROFILES("Perfiles tecnológicos", "Clasificación tecnológica sugerida para perfiles", true),
    QUESTION_TYPES("Tipos de pregunta", "Tipos de respuesta admitidos por el Banco de Preguntas", false),
    DIFFICULTIES("Dificultades", "Niveles de dificultad disponibles para las preguntas", false);

    private final String label;
    private final String description;
    private final boolean tenantAware;

    CatalogType(String label, String description, boolean tenantAware) {
        this.label = label;
        this.description = description;
        this.tenantAware = tenantAware;
    }

    public String label() { return label; }
    public String description() { return description; }
    public boolean tenantAware() { return tenantAware; }
}
