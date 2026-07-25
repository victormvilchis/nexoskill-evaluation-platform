package com.nexoskill.evaluation.questionbank.infrastructure.persistence;

import com.nexoskill.evaluation.questionbank.domain.model.QuestionStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "QUESTION")
public class QuestionJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "QUESTION_ID")
    private Long id;

    @Column(name = "PUBLIC_ID", nullable = false, unique = true, length = 36)
    private String publicId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "TYPE_CODE", nullable = false)
    private QuestionTypeJpaEntity type;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "DIFFICULTY_CODE", nullable = false)
    private QuestionDifficultyJpaEntity difficulty;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "QUESTION_CATEGORY_RELATION",
            joinColumns = @JoinColumn(name = "QUESTION_ID"),
            inverseJoinColumns = @JoinColumn(name = "CATEGORY_ID"))
    @OrderBy("name ASC")
    private Set<QuestionCategoryJpaEntity> categories = new LinkedHashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 30)
    private QuestionStatus status;

    @Lob
    @Column(name = "STATEMENT_TEXT", nullable = false)
    private String statement;

    @Lob
    @Column(name = "EXPLANATION_TEXT")
    private String explanation;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "PROMPT_MEDIA_ID")
    private QuestionMediaJpaEntity promptMedia;

    @Column(name = "CODE_LANGUAGE", length = 40)
    private String codeLanguage;

    @Lob
    @Column(name = "CODE_CONTENT")
    private String codeContent;

    @Lob
    @Column(name = "ACCEPTED_ANSWERS_JSON")
    private String acceptedAnswersJson;

    @Column(name = "ANSWER_CASE_SENSITIVE", nullable = false)
    private Integer caseSensitive;

    @Column(name = "MANUAL_REVIEW", nullable = false)
    private Integer manualReview;

    @Column(name = "NUMERIC_MIN")
    private BigDecimal numericMin;

    @Column(name = "NUMERIC_MAX")
    private BigDecimal numericMax;

    @Column(name = "NUMERIC_TOLERANCE")
    private BigDecimal numericTolerance;

    @Column(name = "RESPONSE_MAX_LENGTH")
    private Integer responseMaxLength;

    @Column(name = "CREATED_BY", nullable = false)
    private Long createdBy;

    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;

    @Column(name = "UPDATED_BY")
    private Long updatedBy;

    @Column(name = "UPDATED_AT")
    private Instant updatedAt;

    @Column(name = "DELETED_AT")
    private Instant deletedAt;

    @Column(name = "DELETED_BY")
    private Long deletedBy;

    @Column(name = "DELETE_REASON", length = 500)
    private String deleteReason;

    @Version
    @Column(name = "VERSION_NO", nullable = false)
    private long version;

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("optionOrder ASC")
    private List<QuestionOptionJpaEntity> options = new ArrayList<>();

    protected QuestionJpaEntity() {
    }

    public static QuestionJpaEntity create(String id, QuestionTypeJpaEntity type,
            QuestionDifficultyJpaEntity difficulty, Set<QuestionCategoryJpaEntity> cats, String statement,
            String explanation, QuestionMediaJpaEntity prompt, String lang, String code, String answers, boolean cs,
            boolean manual, BigDecimal min, BigDecimal max, BigDecimal tolerance, Integer maxLength, Long actor,
            Instant now) {
        var entity = new QuestionJpaEntity();
        entity.publicId = id;
        entity.status = QuestionStatus.ACTIVE;
        entity.createdBy = actor;
        entity.createdAt = now;
        entity.apply(type, difficulty, cats, statement, explanation, prompt, lang, code, answers, cs, manual, min, max,
                tolerance, maxLength, actor, now);
        return entity;
    }

    public void apply(QuestionTypeJpaEntity type, QuestionDifficultyJpaEntity difficulty,
            Set<QuestionCategoryJpaEntity> cats, String statement, String explanation, QuestionMediaJpaEntity prompt,
            String lang, String code, String answers, boolean cs, boolean manual, BigDecimal min, BigDecimal max,
            BigDecimal tolerance, Integer maxLength, Long actor, Instant now) {
        this.type = type;
        this.difficulty = difficulty;
        this.categories.clear();
        this.categories.addAll(cats);
        this.statement = statement;
        this.explanation = explanation;
        this.promptMedia = prompt;
        this.codeLanguage = lang;
        this.codeContent = code;
        this.acceptedAnswersJson = answers;
        this.caseSensitive = cs ? 1 : 0;
        this.manualReview = manual ? 1 : 0;
        this.numericMin = min;
        this.numericMax = max;
        this.numericTolerance = tolerance;
        this.responseMaxLength = maxLength;
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    public void addOption(QuestionOptionJpaEntity option) {
        options.add(option);
    }

    public void clearOptions() {
        options.clear();
    }

    public void changeStatus(QuestionStatus nextStatus, Long actor, Instant now) {
        if (status == QuestionStatus.DELETED) {
            throw new IllegalStateException("A deleted question must be restored before changing its status.");
        }
        status = nextStatus;
        updatedBy = actor;
        updatedAt = now;
    }

    public void softDelete(Long actor, Instant now, String reason) {
        status = QuestionStatus.DELETED;
        deletedAt = now;
        deletedBy = actor;
        deleteReason = reason;
        updatedBy = actor;
        updatedAt = now;
    }

    public void restore(Long actor, Instant now) {
        status = QuestionStatus.ARCHIVED;
        deletedAt = null;
        deletedBy = null;
        deleteReason = null;
        updatedBy = actor;
        updatedAt = now;
    }

    public Long getId() { return id; }
    public String getPublicId() { return publicId; }
    public QuestionTypeJpaEntity getType() { return type; }
    public QuestionDifficultyJpaEntity getDifficulty() { return difficulty; }
    public Set<QuestionCategoryJpaEntity> getCategories() { return Set.copyOf(categories); }
    public QuestionStatus getStatus() { return status; }
    public String getStatement() { return statement; }
    public String getExplanation() { return explanation; }
    public QuestionMediaJpaEntity getPromptMedia() { return promptMedia; }
    public String getCodeLanguage() { return codeLanguage; }
    public String getCodeContent() { return codeContent; }
    public String getAcceptedAnswersJson() { return acceptedAnswersJson; }
    public boolean isCaseSensitive() { return Integer.valueOf(1).equals(caseSensitive); }
    public boolean isManualReview() { return Integer.valueOf(1).equals(manualReview); }
    public BigDecimal getNumericMin() { return numericMin; }
    public BigDecimal getNumericMax() { return numericMax; }
    public BigDecimal getNumericTolerance() { return numericTolerance; }
    public Integer getResponseMaxLength() { return responseMaxLength; }
    public List<QuestionOptionJpaEntity> getOptions() { return List.copyOf(options); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public Long getDeletedBy() { return deletedBy; }
    public String getDeleteReason() { return deleteReason; }
    public long getVersion() { return version; }
}
