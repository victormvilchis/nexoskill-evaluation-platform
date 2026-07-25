package com.nexoskill.evaluation.questionbank.infrastructure.persistence;

import com.nexoskill.evaluation.questionbank.domain.model.QuestionStatus;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    @Enumerated(EnumType.STRING)
    @Column(name = "VERSION_STATUS", nullable = false, length = 30)
    private QuestionStatus status;

    @Column(name = "CHANGE_SUMMARY", length = 500)
    private String changeSummary;

    @Column(name = "STATUS_CHANGED_BY")
    private Long statusChangedBy;

    @Column(name = "STATUS_CHANGED_AT")
    private Instant statusChangedAt;

    @Column(name = "PUBLISHED_AT")
    private Instant publishedAt;

    @Column(name = "CREATED_BY", nullable = false)
    private Long createdBy;

    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "version", cascade = CascadeType.ALL,
            orphanRemoval = true, fetch = FetchType.EAGER)
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
        return create(question, versionNumber, statement, explanation,
                null, createdBy, createdAt);
    }

    public static QuestionVersionJpaEntity create(
            QuestionJpaEntity question,
            int versionNumber,
            String statement,
            String explanation,
            String changeSummary,
            Long createdBy,
            Instant createdAt) {
        QuestionVersionJpaEntity entity = new QuestionVersionJpaEntity();
        entity.question = question;
        entity.versionNumber = versionNumber;
        entity.statement = statement;
        entity.explanation = explanation;
        entity.status = QuestionStatus.DRAFT;
        entity.changeSummary = changeSummary;
        entity.createdBy = createdBy;
        entity.createdAt = createdAt;
        return entity;
    }

    public void updateDraft(
            String statement,
            String explanation,
            String changeSummary,
            List<QuestionOptionJpaEntity> replacementOptions) {
        if (status != QuestionStatus.DRAFT) {
            throw new BusinessException(
                    "QUESTION_VERSION_IMMUTABLE",
                    "Solo una versión en borrador puede modificarse directamente."
            );
        }
        this.statement = statement;
        this.explanation = explanation;
        this.changeSummary = changeSummary;
        options.clear();
        options.addAll(replacementOptions);
    }

    public void transitionTo(QuestionStatus target, Long actorUserId, Instant now) {
        status = target;
        statusChangedBy = actorUserId;
        statusChangedAt = now;
        if (target == QuestionStatus.PUBLISHED) publishedAt = now;
    }

    public void addOption(QuestionOptionJpaEntity option) { options.add(option); }

    public Long getId() { return id; }
    public int getVersionNumber() { return versionNumber; }
    public String getStatement() { return statement; }
    public String getExplanation() { return explanation; }
    public QuestionStatus getStatus() { return status; }
    public String getChangeSummary() { return changeSummary; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getStatusChangedAt() { return statusChangedAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public List<QuestionOptionJpaEntity> getOptions() { return List.copyOf(options); }
}
