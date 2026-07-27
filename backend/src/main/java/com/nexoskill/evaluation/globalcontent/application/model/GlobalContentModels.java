package com.nexoskill.evaluation.globalcontent.application.model;

import com.nexoskill.evaluation.globalcontent.domain.model.*;
import com.nexoskill.evaluation.organizations.domain.model.ContentScope;
import java.time.Instant;
import java.util.List;

public final class GlobalContentModels {
    private GlobalContentModels() {}

    public record ReviewFilter(
            String query,
            String organizationPublicId,
            GlobalContentType contentType,
            ContentScope scope,
            String status,
            Long creatorUserId,
            Instant createdFrom,
            Instant createdTo,
            Boolean promoted,
            Boolean distributed,
            int page,
            int size) {}

    public record ContentResource(
            GlobalContentType contentType,
            Long internalId,
            String publicId,
            String name,
            String description,
            String status,
            ContentScope scope,
            Long ownerOrganizationId,
            String ownerOrganizationPublicId,
            String ownerOrganizationName,
            Long createdBy,
            Instant createdAt,
            Long updatedBy,
            Instant updatedAt,
            long version,
            boolean promoted,
            boolean distributed,
            String functionalHash) {}

    public record ReviewPage(
            List<ContentResource> content,
            int page,
            int size,
            long totalElements,
            int totalPages) {}

    public record Dependency(
            GlobalContentType contentType,
            Long internalId,
            String publicId,
            String name,
            ContentScope scope,
            Long ownerOrganizationId,
            long version,
            boolean globalEquivalentAvailable,
            String globalEquivalentPublicId) {}

    public record PromotionPreview(
            ContentResource source,
            List<Dependency> dependencies,
            List<ContentResource> possibleDuplicates,
            boolean promotable,
            List<String> warnings) {}

    public record PromoteCommand(
            GlobalContentType contentType,
            String sourcePublicId,
            boolean includeDependencies,
            DuplicateResolution duplicateResolution,
            String existingGlobalPublicId,
            String notes) {}

    public record PromotionView(
            String publicId,
            GlobalContentType contentType,
            String sourceOrganizationPublicId,
            String sourceOrganizationName,
            String sourceContentPublicId,
            String sourceContentName,
            long sourceVersion,
            String globalContentPublicId,
            long globalVersion,
            EditorialStatus status,
            String notes,
            Long promotedBy,
            Instant promotedAt,
            Long reviewedBy,
            Instant reviewedAt,
            Long publishedBy,
            Instant publishedAt,
            String rejectionReason,
            List<Dependency> dependencies) {}

    public record GrantCommand(
            String organizationPublicId,
            GlobalContentType contentType,
            String globalContentPublicId,
            long globalVersion,
            DistributionMode distributionMode,
            AccessMode accessMode,
            boolean cloningAllowed,
            boolean organizationEditable,
            UpdatePolicy updatePolicy,
            Instant availableFrom,
            Instant expiresAt) {}

    public record GrantView(
            String publicId,
            String organizationPublicId,
            String organizationName,
            GlobalContentType contentType,
            String globalContentPublicId,
            long globalVersion,
            GrantStatus status,
            DistributionMode distributionMode,
            AccessMode accessMode,
            boolean cloningAllowed,
            boolean organizationEditable,
            UpdatePolicy updatePolicy,
            Instant availableFrom,
            Instant expiresAt,
            String targetContentPublicId,
            Instant enabledAt) {}

    public record DistributionCommand(
            GlobalContentType contentType,
            String globalContentPublicId,
            long globalVersion,
            List<String> organizationPublicIds,
            DistributionMode distributionMode,
            AccessMode accessMode,
            boolean cloningAllowed,
            boolean organizationEditable,
            UpdatePolicy updatePolicy,
            Instant availableFrom,
            Instant expiresAt,
            String notes) {}

    public record DistributionResult(
            String organizationPublicId,
            String organizationName,
            DistributionResultStatus status,
            String grantPublicId,
            String targetContentPublicId,
            String errorCode,
            String errorMessage,
            int attemptNumber,
            Instant processedAt) {}

    public record DistributionJobView(
            String publicId,
            GlobalContentType contentType,
            String globalContentPublicId,
            long globalVersion,
            DistributionMode distributionMode,
            DistributionJobStatus status,
            int totalOrganizations,
            int processedOrganizations,
            int successfulOrganizations,
            int failedOrganizations,
            int skippedOrganizations,
            Instant requestedAt,
            Instant startedAt,
            Instant finishedAt,
            List<DistributionResult> results) {}

    public record ReplicatedResource(
            GlobalContentType contentType,
            Long internalId,
            String publicId,
            boolean existing,
            boolean customized,
            SyncStatus syncStatus) {}

    public record VersionView(
            String publicId,
            GlobalContentType contentType,
            String contentPublicId,
            long version,
            EditorialStatus status,
            String functionalHash,
            String changeNotes,
            Long createdBy,
            Instant createdAt,
            Long publishedBy,
            Instant publishedAt) {}
}
