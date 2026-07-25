package com.nexoskill.evaluation.questionbank.infrastructure.persistence;

import com.nexoskill.evaluation.questionbank.domain.model.CatalogStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "QUESTION_DIFFICULTY_CATALOG")
public class QuestionDifficultyJpaEntity {

    @Id
    @Column(name = "DIFFICULTY_CODE", length = 40)
    private String code;

    @Column(name = "DIFFICULTY_NAME", nullable = false, length = 100)
    private String name;

    @Column(name = "SORT_ORDER", nullable = false)
    private int sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private CatalogStatus status;

    protected QuestionDifficultyJpaEntity() {
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public int getSortOrder() { return sortOrder; }
    public CatalogStatus getStatus() { return status; }
}
