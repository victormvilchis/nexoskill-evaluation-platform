package com.nexoskill.evaluation.organizations.infrastructure.persistence;

/** Proyección agregada para evitar cargar estudiantes y evitar consultas N+1. */
public interface OrganizationSearchRow {
    OrganizationJpaEntity getOrganization();
    long getStudentCount();
}
