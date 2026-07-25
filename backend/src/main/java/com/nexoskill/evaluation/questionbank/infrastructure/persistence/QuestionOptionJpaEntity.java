package com.nexoskill.evaluation.questionbank.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "QUESTION_OPTION")
public class QuestionOptionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "QUESTION_OPTION_ID")
    private Long id;

    @Column(name = "PUBLIC_ID", nullable = false, unique = true, length = 36)
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "QUESTION_VERSION_ID", nullable = false)
    private QuestionVersionJpaEntity version;

    @Column(name = "OPTION_ORDER", nullable = false)
    private int optionOrder;

    @Lob
    @Column(name = "OPTION_TEXT", nullable = false)
    private String text;

    @Column(name = "IS_CORRECT", nullable = false)
    private Integer correct;

    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;

    protected QuestionOptionJpaEntity() {
    }

    public static QuestionOptionJpaEntity create(
            QuestionVersionJpaEntity version,
            String publicId,
            int optionOrder,
            String text,
            boolean correct,
            Instant createdAt) {
        QuestionOptionJpaEntity entity = new QuestionOptionJpaEntity();
        entity.version = version;
        entity.publicId = publicId;
        entity.optionOrder = optionOrder;
        entity.text = text;
        entity.correct = correct ? 1 : 0;
        entity.createdAt = createdAt;
        return entity;
    }

    public String getPublicId() { return publicId; }
    public int getOptionOrder() { return optionOrder; }
    public String getText() { return text; }
    public boolean isCorrect() { return Integer.valueOf(1).equals(correct); }
}
