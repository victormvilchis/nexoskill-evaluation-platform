package com.nexoskill.evaluation.certifications.application;

import com.nexoskill.evaluation.certifications.domain.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class CertificationModels {
    private CertificationModels() {}

    public record Availability(boolean appliesCertifications, boolean operatorAllowed,
            String organizationPublicId, String organizationName) {}

    public record CatalogItem(String publicId, String code, String name, String suggestedTechnologicalProfile) {}
    public record EnumOption(String value, String label) {}
    public record Catalogs(List<CatalogItem> profiles, List<CatalogItem> technologies,
            List<EnumOption> technologicalProfiles, List<EnumOption> certificationStatuses,
            List<EnumOption> examStatuses, List<EnumOption> certificationTypes) {}

    public record ProfileView(String publicId, String professionalProfilePublicId,
            String professionalProfileName, String certificationTechnologyPublicId,
            String certificationTechnologyName, LocalDate enrollmentDate,
            String technologicalProfile, Long version) {}

    public record RequirementView(String publicId, CertificationType type, boolean applies,
            CertificationStatus certificationStatus, CertificationExamStatus examStatus,
            LocalDate calculatedDeadline, LocalDate manualDeadline, LocalDate effectiveDeadline,
            String deadlineOverrideReason, LocalDate applicationDate, BigDecimal score,
            Integer currentAttempt, String actionsToTake, String observations, Long version) {}

    public record AttemptView(String publicId, CertificationType type, int attemptNumber,
            LocalDate scheduledDate, LocalDate applicationDate, CertificationExamStatus examStatus,
            BigDecimal score, String result, String observations, Instant createdAt) {}

    public record HistoryView(String publicId, String eventType, String previousValues,
            String newValues, String reason, Instant changedAt) {}

    public record StudentCertificationDetail(String studentPublicId, String studentName,
            String organizationPublicId, String organizationName, boolean appliesCertifications,
            ProfileView profile, List<RequirementView> requirements, List<AttemptView> attempts,
            List<HistoryView> history) {}

    public record SaveCommand(String professionalProfilePublicId,
            String certificationTechnologyPublicId, LocalDate enrollmentDate,
            String technologicalProfile, Long profileVersion,
            List<RequirementCommand> requirements, List<AttemptCommand> newAttempts) {}

    public record RequirementCommand(CertificationType type, boolean applies,
            CertificationStatus certificationStatus, CertificationExamStatus examStatus,
            LocalDate manualDeadline, String deadlineOverrideReason, LocalDate applicationDate,
            BigDecimal score, Integer currentAttempt, String actionsToTake, String observations,
            Long version) {}

    public record AttemptCommand(CertificationType type, int attemptNumber,
            LocalDate scheduledDate, LocalDate applicationDate, CertificationExamStatus examStatus,
            BigDecimal score, String result, String observations) {}
}
