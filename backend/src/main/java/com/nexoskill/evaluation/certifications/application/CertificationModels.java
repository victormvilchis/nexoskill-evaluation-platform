package com.nexoskill.evaluation.certifications.application;

import com.nexoskill.evaluation.certifications.domain.CertificationExamStatus;
import com.nexoskill.evaluation.certifications.domain.CertificationLevel;
import com.nexoskill.evaluation.certifications.domain.CertificationProcessType;
import com.nexoskill.evaluation.certifications.domain.CertificationTrackingStatus;
import com.nexoskill.evaluation.certifications.domain.CertificationType;
import com.nexoskill.evaluation.certifications.domain.CertificationValidityStatus;
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
            List<CatalogItem> technologicalProfiles, List<EnumOption> levels,
            List<EnumOption> trackingStatuses, List<EnumOption> examStatuses,
            List<EnumOption> certificationTypes) {}

    public record StudentSummary(String publicId, String displayName, String organizationPublicId,
            String organizationName, String status, LocalDate validFrom, LocalDate expiresAt,
            LocalDate admissionDate, CatalogItem professionalProfile, CatalogItem technologicalProfile) {}
    public record Applicability(boolean technological, boolean developmentSecurity,
            boolean normativeTesting, boolean one, boolean agile) {}
    public record Metrics(int applicableAreas, int pending, int scheduled, int approved, int notApproved,
            int valid, int expiringSoon, int expired, int pendingRecertifications) {}

    public record CycleView(String publicId, CertificationType type, String technologyPublicId,
            String technologyName, CertificationLevel certificationLevel, boolean primary,
            CertificationProcessType processType, CertificationTrackingStatus trackingStatus,
            LocalDate deadlineDate, LocalDate scheduledDate, LocalDate applicationDate,
            Boolean approved, LocalDate expirationDate, CertificationValidityStatus validityStatus,
            String previousApprovedCyclePublicId, String actionsToTake, String softtekManagement,
            String observations, boolean active, BigDecimal latestScore, Integer attemptCount,
            Long version) {}
    public record AttemptView(String publicId, String cyclePublicId, int attemptNumber,
            LocalDate scheduledDate, LocalDate applicationDate, CertificationExamStatus examStatus,
            BigDecimal score, Boolean approved, String result, String observations,
            Instant createdAt, Long version) {}
    public record HistoryView(String publicId, String eventType, String previousValues,
            String newValues, String reason, Instant changedAt) {}
    public record PageResult<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
        public PageResult { content = content == null ? List.of() : List.copyOf(content); }
    }
    public record StudentCertificationDetail(StudentSummary student, boolean appliesCertifications,
            Applicability applicability, Metrics metrics, List<CycleView> cycles) {}

    public record SaveCommand(List<CycleCommand> cycles) {}
    public record CycleCommand(String publicId, CertificationType type, String technologyPublicId,
            CertificationLevel certificationLevel, boolean primary, CertificationTrackingStatus trackingStatus,
            LocalDate scheduledDate, LocalDate applicationDate, Boolean approved,
            String actionsToTake, String softtekManagement, String observations, boolean active, Long version,
            List<AttemptCommand> attempts) {
        public CycleCommand {
            attempts = attempts == null ? List.of() : List.copyOf(attempts);
        }
    }
    public record AttemptCommand(String publicId, LocalDate scheduledDate, LocalDate applicationDate,
            CertificationExamStatus examStatus, BigDecimal score, Boolean approved,
            String result, String observations, Long version) {}
}
