package com.nexoskill.evaluation.dashboard.application.model;

import java.time.Instant;
import java.util.List;

public final class DashboardModels {
    private DashboardModels() {
    }

    public record Filter(String organizationPublicId, String role, String technology,
            String collaboratorStatus, String certificationType, String certificationState) {
    }

    public record Option(String value, String label) {
    }

    public record Scope(boolean administrator, boolean global, String organizationPublicId,
            String organizationName, boolean certificationsEnabled) {
    }

    public record Kpis(long activeCollaborators, long talentBank, Double certificationCompliance,
            int applicableCertifications, int coveredCertifications, int expiringSoon,
            int expired, int pendingRecertifications) {
    }

    public record ChartPoint(String key, String label, long value) {
    }

    public record CertificationTypePoint(String key, String label, long applicable,
            long covered, long pending, long expiringSoon, long expired, Double compliance) {
    }

    public record OrganizationPoint(String publicId, String label, long activeCollaborators,
            long talentBank, long valid, long expiringSoon, long expired) {
    }

    public record AttentionItem(String key, String label, long value, String severity,
            String description) {
    }

    public record CertificationFocusDetail(String studentPublicId, String collaborator, String role,
            String technology, String status, String certificationType, String certificationLabel,
            String expirationDate, boolean secondAttemptFailed) {
    }

    public record FilterOptions(List<Option> organizations, List<Option> roles,
            List<Option> technologies, List<Option> collaboratorStatuses,
            List<Option> certificationTypes, List<Option> certificationStates) {
    }

    public record Overview(Instant generatedAt, Scope scope, boolean canPersonalize,
            Filter appliedFilters, FilterOptions filterOptions, Kpis kpis,
            List<ChartPoint> certificationStatus, List<CertificationTypePoint> certificationTypes,
            List<ChartPoint> expirations, List<ChartPoint> technologies, List<ChartPoint> roles,
            List<ChartPoint> talentBank, List<OrganizationPoint> organizations,
            List<ChartPoint> certificationFocus, List<CertificationFocusDetail> certificationFocusDetails,
            List<AttentionItem> attention) {
    }

    public record ComponentDefinition(String code, String title, String category,
            List<String> allowedSizes, String defaultSize, boolean administratorOnly) {
    }

    public record ComponentPreference(String code, String size, int order) {
    }

    public record Configuration(boolean personalized, boolean canPersonalize,
            List<ComponentPreference> components, List<ComponentDefinition> catalog) {
    }

    public record SaveConfigurationCommand(List<ComponentPreference> components) {
    }
}
