package com.nexoskill.evaluation.paths.application;

import java.time.OffsetDateTime;
import java.util.List;

public final class PathModels {
    private PathModels() {}

    public record PathCommand(String name, String description, List<String> collectionPublicIds, Long version) {
        public PathCommand {
            collectionPublicIds = collectionPublicIds == null ? List.of() : List.copyOf(collectionPublicIds);
        }
    }

    public record PathSummary(String publicId, String code, String name, String description, String status,
            String contentScope, String organizationPublicId, String organizationName,
            int collectionCount, int formCount, OffsetDateTime updatedAt, Long version) {}

    public record PathCollectionView(int order, String collectionPublicId, String name, String description,
            String status, String contentScope, String organizationPublicId, String organizationName,
            int formCount) {}

    public record PathDetail(String publicId, String code, String name, String description, String status,
            String contentScope, String organizationPublicId, String organizationName,
            List<PathCollectionView> collections, OffsetDateTime createdAt, OffsetDateTime updatedAt, Long version) {
        public PathDetail {
            collections = collections == null ? List.of() : List.copyOf(collections);
        }
    }

    public record CollectionOption(String publicId, String name, String description, String status,
            String contentScope, String organizationPublicId, String organizationName, int formCount) {}
}
