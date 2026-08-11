package com.nexoskill.evaluation.development.application.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public final class StudentDevelopmentModels {
    private StudentDevelopmentModels() {}

    public record Option(String value, String label, long count) {}

    public record Profile(String displayName, String firstName, String professionalProfile,
            String technologicalProfile, String technology, String expertiseLevel) {}

    public record HealthCheck(String level, String label, String description) {}

    public record ActionLink(String action, String label, String target) {}

    public record Focus(String type, String severity, String title, String description,
            List<ActionLink> actions) {
        public Focus {
            actions = actions == null ? List.of() : List.copyOf(actions);
        }
    }

    public record ContinueItem(String type, String title, String subtitle, Integer current,
            Integer total, BigDecimal progressPercent, String target) {}

    public record CertificationCard(String type, String label, String status, String statusLabel,
            LocalDate deadlineDate, LocalDate applicationDate, LocalDate expirationDate, BigDecimal score,
            Integer attemptNumber, boolean secondAttemptFailed, String technology, String level) {}

    public record EvaluationCard(String assignmentPublicId, String formPublicId, String title,
            String description, String status, Integer questionCount, Integer durationMinutes,
            BigDecimal passingScore, Integer maxAttempts, int attemptsUsed, OffsetDateTime dueAt,
            boolean canStart, boolean canResume, String activeAttemptPublicId, List<String> pathNames) {
        public EvaluationCard {
            pathNames = pathNames == null ? List.of() : List.copyOf(pathNames);
        }
    }

    public record PathStage(String collectionPublicId, String title, int order, String state,
            int completedForms, int totalForms, String nextEvaluationAssignmentPublicId) {}

    public record PathCard(String assignmentPublicId, String pathPublicId, String name,
            String description, String status, Integer completedStages, Integer totalStages,
            BigDecimal progressPercent, String nextStageTitle, String nextEvaluationAssignmentPublicId,
            List<PathStage> stages) {
        public PathCard {
            stages = stages == null ? List.of() : List.copyOf(stages);
        }
    }

    public record PathOption(String publicId, String name, String description, int collectionCount,
            int formCount, String contentScope, String organizationName) {}

    public record AssignedPathAdmin(String assignmentPublicId, String pathPublicId, String name,
            String description, String status, int collectionCount, int formCount, OffsetDateTime assignedAt) {}

    public record PreparationPoint(String key, String label, int answered, int correct,
            BigDecimal percentage) {}

    public record TrendPoint(String period, int answered, int correct, BigDecimal percentage) {}

    public record Recommendation(String type, String severity, String title, String description,
            String actionLabel, String target) {}

    public record ActivityItem(String type, Instant occurredAt, String title, String description) {}

    public record HomeView(Profile profile, HealthCheck healthCheck, Focus focus,
            ContinueItem continueItem, boolean certificationsEnabled, long availableStudyQuestions,
            List<CertificationCard> certifications, List<EvaluationCard> evaluations, List<PathCard> paths,
            List<PreparationPoint> preparation, List<PreparationPoint> strengths,
            List<PreparationPoint> improvementAreas, List<Recommendation> recommendations,
            List<TrendPoint> trend, List<ActivityItem> recentActivity) {
        public HomeView {
            certifications = certifications == null ? List.of() : List.copyOf(certifications);
            evaluations = evaluations == null ? List.of() : List.copyOf(evaluations);
            paths = paths == null ? List.of() : List.copyOf(paths);
            preparation = preparation == null ? List.of() : List.copyOf(preparation);
            strengths = strengths == null ? List.of() : List.copyOf(strengths);
            improvementAreas = improvementAreas == null ? List.of() : List.copyOf(improvementAreas);
            recommendations = recommendations == null ? List.of() : List.copyOf(recommendations);
            trend = trend == null ? List.of() : List.copyOf(trend);
            recentActivity = recentActivity == null ? List.of() : List.copyOf(recentActivity);
        }
    }

    public record StudyOptions(List<Option> technologies, List<Option> categories,
            long availableQuestions, long reviewErrorQuestions) {
        public StudyOptions {
            technologies = technologies == null ? List.of() : List.copyOf(technologies);
            categories = categories == null ? List.of() : List.copyOf(categories);
        }
    }

    public record StartPracticeCommand(String mode, Integer questionCount, String technologyPublicId,
            String categoryPublicId) {}

    public record QuestionOption(String publicId, String text) {}

    public record PracticeQuestion(String sessionQuestionPublicId, String questionPublicId,
            int position, int total, String type, String statement, String codeLanguage, String codeContent,
            String technology, List<String> categories, List<QuestionOption> options, boolean answered,
            Boolean correct, List<String> selectedOptionPublicIds, List<String> correctOptionPublicIds,
            String explanation) {
        public PracticeQuestion {
            categories = categories == null ? List.of() : List.copyOf(categories);
            options = options == null ? List.of() : List.copyOf(options);
            selectedOptionPublicIds = selectedOptionPublicIds == null ? List.of() : List.copyOf(selectedOptionPublicIds);
            correctOptionPublicIds = correctOptionPublicIds == null ? List.of() : List.copyOf(correctOptionPublicIds);
        }
    }

    public record PracticeSession(String publicId, String mode, String status, String technology,
            String category, int requestedCount, int totalQuestions, int answeredQuestions, int correctAnswers,
            Instant startedAt, Instant completedAt, boolean immediateFeedback, PracticeQuestion currentQuestion,
            List<PracticeQuestion> questions) {
        public PracticeSession {
            questions = questions == null ? List.of() : List.copyOf(questions);
        }
    }

    public record PracticeAnswerCommand(List<String> selectedOptionPublicIds) {
        public PracticeAnswerCommand {
            selectedOptionPublicIds = selectedOptionPublicIds == null ? List.of() : List.copyOf(selectedOptionPublicIds);
        }
    }

    public record PracticeFeedback(Boolean correct, List<String> correctOptionPublicIds,
            String explanation, int answeredQuestions, int correctAnswers, boolean completed,
            PracticeQuestion nextQuestion) {
        public PracticeFeedback {
            correctOptionPublicIds = correctOptionPublicIds == null ? List.of() : List.copyOf(correctOptionPublicIds);
        }
    }

    public record PracticeResult(String publicId, String mode, int totalQuestions, int answeredQuestions,
            int correctAnswers, BigDecimal percentage, List<PreparationPoint> byTopic,
            List<String> incorrectQuestionPublicIds, Instant completedAt) {
        public PracticeResult {
            byTopic = byTopic == null ? List.of() : List.copyOf(byTopic);
            incorrectQuestionPublicIds = incorrectQuestionPublicIds == null ? List.of() : List.copyOf(incorrectQuestionPublicIds);
        }
    }

    public record AssignEvaluationCommand(String formPublicId, OffsetDateTime dueAt) {}
    public record AssignPathCommand(String pathPublicId) {}

    public record EvaluationAttemptQuestion(String attemptQuestionPublicId, String questionPublicId,
            int position, int total, String type, String statement, String codeLanguage, String codeContent,
            List<QuestionOption> options, List<QuestionOption> matchingRightOptions, BigDecimal points,
            boolean answered, List<String> selectedOptionPublicIds, Map<String, String> matchingPairs,
            String textAnswer) {
        public EvaluationAttemptQuestion {
            options = options == null ? List.of() : List.copyOf(options);
            matchingRightOptions = matchingRightOptions == null ? List.of() : List.copyOf(matchingRightOptions);
            selectedOptionPublicIds = selectedOptionPublicIds == null ? List.of() : List.copyOf(selectedOptionPublicIds);
            matchingPairs = matchingPairs == null ? Map.of() : Map.copyOf(matchingPairs);
        }
    }

    public record EvaluationAttempt(String publicId, String assignmentPublicId, String formPublicId,
            String title, String status, int attemptNumber, Integer maxAttempts, Integer durationMinutes,
            BigDecimal passingScore, Instant startedAt, Instant expiresAt, Instant submittedAt,
            BigDecimal score, Boolean passed, boolean showProgress, boolean showQuestionNumbers,
            boolean allowSaveResume, EvaluationAttemptQuestion currentQuestion, List<EvaluationAttemptQuestion> questions) {
        public EvaluationAttempt {
            questions = questions == null ? List.of() : List.copyOf(questions);
        }
    }

    public record EvaluationAnswerCommand(List<String> selectedOptionPublicIds,
            Map<String, String> matchingPairs, String textAnswer) {
        public EvaluationAnswerCommand {
            selectedOptionPublicIds = selectedOptionPublicIds == null ? List.of() : List.copyOf(selectedOptionPublicIds);
            matchingPairs = matchingPairs == null ? Map.of() : Map.copyOf(matchingPairs);
        }
    }

    public record EvaluationAnswerReview(String attemptQuestionPublicId, Boolean correct,
            List<String> correctOptionPublicIds, Map<String, String> correctMatchingPairs,
            List<String> acceptedTextAnswers) {
        public EvaluationAnswerReview {
            correctOptionPublicIds = correctOptionPublicIds == null ? List.of() : List.copyOf(correctOptionPublicIds);
            correctMatchingPairs = correctMatchingPairs == null ? Map.of() : Map.copyOf(correctMatchingPairs);
            acceptedTextAnswers = acceptedTextAnswers == null ? List.of() : List.copyOf(acceptedTextAnswers);
        }
    }

    public record EvaluationSubmission(String attemptPublicId, String status, BigDecimal score,
            Boolean passed, int answeredQuestions, int totalQuestions, String message,
            List<EvaluationAnswerReview> answerReview) {
        public EvaluationSubmission {
            answerReview = answerReview == null ? List.of() : List.copyOf(answerReview);
        }
    }
}
