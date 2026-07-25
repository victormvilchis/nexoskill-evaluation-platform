package com.nexoskill.evaluation.questionbank.infrastructure.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "QUESTION_VERSION")
public class QuestionVersionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "QUESTION_VERSION_ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "QUESTION_ID", nullable = false)
    private QuestionJpaEntity question;

    @Column(name = "VERSION_NUMBER", nullable = false)
    private int versionNumber;

    @Lob
    @Column(name = "STATEMENT_TEXT", nullable = false)
    private String statement;

    @Lob
    @Column(name = "EXPLANATION_TEXT")
    private String explanation;

    @Column(name = "CREATED_BY", nullable = false)
    private Long createdBy;

    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;

    @OneToMany(
            mappedBy = "version",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.EAGER
    )
    @OrderBy("optionOrder ASC")
    private List<QuestionOptionJpaEntity> options = new ArrayList<>();

    protected QuestionVersionJpaEntity() {
    }

    public static QuestionVersionJpaEntity create(
            QuestionJpaEntity question,
            int versionNumber,
            String statement,
            String explanation,
            Long createdBy,
            Instant createdAt) {
        QuestionVersionJpaEntity entity = new QuestionVersionJpaEntity();
        entity.question = question;
        entity.versionNumber = versionNumber;
        entity.statement = statement;
        entity.explanation = explanation;
        entity.createdBy = createdBy;
        entity.createdAt = createdAt;
        return entity;
    }

    public void addOption(QuestionOptionJpaEntity option) {
        options.add(option);
    }

    public Long getId() { return id; }
    public int getVersionNumber() { return versionNumber; }
    public String getStatement() { return statement; }
    public String getExplanation() { return explanation; }
    public List<QuestionOptionJpaEntity> getOptions() { return List.copyOf(options); }
}
