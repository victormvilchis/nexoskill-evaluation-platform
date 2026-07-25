package com.nexoskill.evaluation.forms.application;

import java.time.OffsetDateTime;
import java.util.List;

public final class FormModels {
  private FormModels() {}
  public record FormCommand(String title,String description,String modeCode,java.math.BigDecimal passingScore,Integer maxAttempts,
    boolean retryUntilPassed,boolean acceptResponses,OffsetDateTime startsAt,OffsetDateTime endsAt,Integer durationMinutes,
    boolean showResults,boolean showCorrectAnswers,boolean randomizeQuestions,boolean randomizeOptions,
    boolean showProgress,boolean hideQuestionNumbers,boolean allowSaveResume,boolean oneActiveAttempt,String thankYouMessage,
    Long version,List<SectionCommand> sections) {}
  public record SectionCommand(String publicId,String title,String description,Integer order,List<QuestionItem> questions,List<PoolItem> pools) {}
  public record QuestionItem(String questionPublicId,Integer order,java.math.BigDecimal points,boolean required) {}
  public record PoolItem(String publicId,String sourceType,String sourcePublicId,Integer questionCount,String difficultyCode,Integer order) {}
  public record FormSummary(String publicId,String code,String title,String status,String modeCode,java.math.BigDecimal passingScore,
    Integer sectionCount,Integer questionCount,OffsetDateTime startsAt,OffsetDateTime endsAt,Long version) {}
  public record FormDetail(String publicId,String code,String title,String description,String status,String modeCode,
    java.math.BigDecimal passingScore,Integer maxAttempts,boolean retryUntilPassed,boolean acceptResponses,
    OffsetDateTime startsAt,OffsetDateTime endsAt,Integer durationMinutes,boolean showResults,boolean showCorrectAnswers,
    boolean randomizeQuestions,boolean randomizeOptions,boolean showProgress,boolean hideQuestionNumbers,
    boolean allowSaveResume,boolean oneActiveAttempt,String thankYouMessage,Long version,List<SectionView> sections) {}
  public record SectionView(String publicId,String title,String description,Integer order,List<QuestionView> questions,List<PoolView> pools) {}
  public record QuestionView(String questionPublicId,String statement,Integer order,java.math.BigDecimal points,boolean required) {}
  public record PoolView(String publicId,String sourceType,String sourcePublicId,String sourceName,Integer questionCount,String difficultyCode,Integer order) {}
}
