package com.nexoskill.evaluation.globalcontent.interfaces.rest;

import com.nexoskill.evaluation.authentication.infrastructure.security.AuthenticatedUser;
import com.nexoskill.evaluation.globalcontent.application.model.GlobalContentModels.*;
import com.nexoskill.evaluation.globalcontent.application.service.*;
import com.nexoskill.evaluation.globalcontent.domain.model.*;
import com.nexoskill.evaluation.organizations.domain.model.ContentScope;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/global-content")
public class GlobalContentController {
    private final GlobalContentReviewService review;
    private final ContentPromotionService promotions;
    private final GlobalContentPublicationService publication;
    private final GlobalContentVersionService versions;
    private final OrganizationContentGrantService grants;
    private final ContentDistributionService distributions;

    public GlobalContentController(GlobalContentReviewService review,
            ContentPromotionService promotions,
            GlobalContentPublicationService publication,
            GlobalContentVersionService versions,
            OrganizationContentGrantService grants,
            ContentDistributionService distributions) {
        this.review = review;
        this.promotions = promotions;
        this.publication = publication;
        this.versions = versions;
        this.grants = grants;
        this.distributions = distributions;
    }

    @GetMapping("/review")
    @PreAuthorize("hasAuthority('GLOBAL_CONTENT_REVIEW')")
    public ReviewPage review(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String organizationPublicId,
            @RequestParam(required = false) GlobalContentType contentType,
            @RequestParam(required = false) ContentScope scope,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long creatorUserId,
            @RequestParam(required = false) Instant createdFrom,
            @RequestParam(required = false) Instant createdTo,
            @RequestParam(required = false) Boolean promoted,
            @RequestParam(required = false) Boolean distributed,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return review.review(new ReviewFilter(query, organizationPublicId, contentType, scope, status,
                creatorUserId, createdFrom, createdTo, promoted, distributed, page, size), actor.internalId());
    }

    @GetMapping("/review/{type}/{publicId}")
    @PreAuthorize("hasAuthority('GLOBAL_CONTENT_REVIEW')")
    public ContentResource get(@PathVariable GlobalContentType type, @PathVariable String publicId,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return review.get(type, publicId, actor.internalId());
    }

    @GetMapping("/promotions/preview")
    @PreAuthorize("hasAuthority('GLOBAL_CONTENT_PROMOTE')")
    public PromotionPreview preview(@RequestParam GlobalContentType type,
            @RequestParam String sourcePublicId) {
        return promotions.preview(type, sourcePublicId);
    }

    @GetMapping("/promotions")
    @PreAuthorize("hasAuthority('GLOBAL_CONTENT_REVIEW')")
    public List<PromotionView> promotions() {
        return promotions.list();
    }

    @GetMapping("/promotions/{publicId}")
    @PreAuthorize("hasAuthority('GLOBAL_CONTENT_REVIEW')")
    public PromotionView promotion(@PathVariable String publicId) {
        return promotions.get(publicId);
    }

    @PostMapping("/promotions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('GLOBAL_CONTENT_PROMOTE')")
    public PromotionView promote(@Valid @RequestBody PromotionRequest request,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return promotions.promote(new PromoteCommand(request.contentType(), request.sourcePublicId(),
                request.includeDependencies(), request.duplicateResolution(), request.existingGlobalPublicId(),
                request.notes()), actor.internalId());
    }

    @PostMapping("/promotions/{publicId}/submit-review")
    @PreAuthorize("hasAuthority('GLOBAL_CONTENT_PROMOTE')")
    public PromotionView submitReview(@PathVariable String publicId,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return publication.submitForReview(publicId, actor.internalId());
    }

    @PostMapping("/promotions/{publicId}/publish")
    @PreAuthorize("hasAuthority('GLOBAL_CONTENT_PUBLISH')")
    public PromotionView publish(@PathVariable String publicId,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return publication.publish(publicId, actor.internalId());
    }

    @PostMapping("/promotions/{publicId}/reject")
    @PreAuthorize("hasAuthority('GLOBAL_CONTENT_PUBLISH')")
    public PromotionView reject(@PathVariable String publicId, @Valid @RequestBody ReasonRequest request,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return publication.reject(publicId, request.reason(), actor.internalId());
    }

    @PostMapping("/promotions/{publicId}/archive")
    @PreAuthorize("hasAuthority('GLOBAL_CONTENT_PUBLISH')")
    public PromotionView archive(@PathVariable String publicId,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return publication.archive(publicId, actor.internalId());
    }

    @GetMapping("/versions")
    @PreAuthorize("hasAuthority('GLOBAL_CONTENT_REVIEW')")
    public List<VersionView> versions(@RequestParam GlobalContentType type,
            @RequestParam String contentPublicId) {
        return versions.list(type, contentPublicId);
    }

    @PostMapping("/versions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('GLOBAL_CONTENT_VERSION_MANAGE')")
    public VersionView createVersion(@Valid @RequestBody VersionRequest request,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return versions.createVersion(request.contentType(), request.contentPublicId(), request.notes(),
                actor.internalId());
    }

    @GetMapping("/grants")
    @PreAuthorize("hasAuthority('GLOBAL_CONTENT_REVIEW')")
    public List<GrantView> grants(@RequestParam String organizationPublicId) {
        return grants.list(organizationPublicId);
    }

    @PostMapping("/grants")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('GLOBAL_CONTENT_DISTRIBUTE')")
    public GrantView grant(@Valid @RequestBody GrantRequest request,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return grants.grant(request.toCommand(), actor.internalId());
    }

    @PostMapping("/grants/{publicId}/disable")
    @PreAuthorize("hasAuthority('GLOBAL_CONTENT_DISTRIBUTE')")
    public GrantView disableGrant(@PathVariable String publicId,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return grants.disable(publicId, actor.internalId());
    }

    @PostMapping("/distributions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('GLOBAL_CONTENT_DISTRIBUTE')")
    public DistributionJobView distribute(@Valid @RequestBody DistributionRequest request,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return distributions.distribute(request.toCommand(), actor.internalId());
    }

    @GetMapping("/distributions/{publicId}")
    @PreAuthorize("hasAuthority('GLOBAL_CONTENT_REVIEW')")
    public DistributionJobView distribution(@PathVariable String publicId) {
        return distributions.get(publicId);
    }

    public record PromotionRequest(
            @NotNull GlobalContentType contentType,
            @NotBlank String sourcePublicId,
            boolean includeDependencies,
            @NotNull DuplicateResolution duplicateResolution,
            String existingGlobalPublicId,
            @Size(max = 2000) String notes) {}

    public record ReasonRequest(@NotBlank @Size(max = 1000) String reason) {}

    public record VersionRequest(
            @NotNull GlobalContentType contentType,
            @NotBlank String contentPublicId,
            @Size(max = 2000) String notes) {}

    public record GrantRequest(
            @NotBlank String organizationPublicId,
            @NotNull GlobalContentType contentType,
            @NotBlank String globalContentPublicId,
            @Positive long globalVersion,
            @NotNull DistributionMode distributionMode,
            @NotNull AccessMode accessMode,
            boolean cloningAllowed,
            boolean organizationEditable,
            @NotNull UpdatePolicy updatePolicy,
            Instant availableFrom,
            Instant expiresAt) {
        GrantCommand toCommand() {
            return new GrantCommand(organizationPublicId, contentType, globalContentPublicId, globalVersion,
                    distributionMode, accessMode, cloningAllowed, organizationEditable, updatePolicy,
                    availableFrom, expiresAt);
        }
    }

    public record DistributionRequest(
            @NotNull GlobalContentType contentType,
            @NotBlank String globalContentPublicId,
            @Positive long globalVersion,
            @NotEmpty List<@NotBlank String> organizationPublicIds,
            @NotNull DistributionMode distributionMode,
            @NotNull AccessMode accessMode,
            boolean cloningAllowed,
            boolean organizationEditable,
            @NotNull UpdatePolicy updatePolicy,
            Instant availableFrom,
            Instant expiresAt,
            @Size(max = 2000) String notes) {
        DistributionCommand toCommand() {
            return new DistributionCommand(contentType, globalContentPublicId, globalVersion,
                    List.copyOf(organizationPublicIds), distributionMode, accessMode, cloningAllowed,
                    organizationEditable, updatePolicy, availableFrom, expiresAt, notes);
        }
    }
}
