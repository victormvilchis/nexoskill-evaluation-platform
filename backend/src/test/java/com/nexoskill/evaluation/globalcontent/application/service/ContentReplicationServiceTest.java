package com.nexoskill.evaluation.globalcontent.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.globalcontent.application.model.GlobalContentModels.ContentResource;
import com.nexoskill.evaluation.globalcontent.application.port.out.GlobalContentResourcePort;
import com.nexoskill.evaluation.globalcontent.domain.model.GlobalContentType;
import com.nexoskill.evaluation.globalcontent.domain.model.SyncStatus;
import com.nexoskill.evaluation.globalcontent.infrastructure.persistence.ContentReplicationLinkJpaEntity;
import com.nexoskill.evaluation.globalcontent.infrastructure.persistence.ContentReplicationLinkRepository;
import com.nexoskill.evaluation.organizations.domain.model.ContentScope;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ContentReplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-26T18:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void repeatedReplicationReturnsTheExistingCopyWithoutCreatingADuplicate() {
        GlobalContentResourcePort resources = mock(GlobalContentResourcePort.class);
        GlobalContentVersionService versions = mock(GlobalContentVersionService.class);
        ContentReplicationLinkRepository links = mock(ContentReplicationLinkRepository.class);
        AuditLogPort audit = mock(AuditLogPort.class);
        ContentReplicationService service = new ContentReplicationService(resources, versions, links, audit, CLOCK);

        ContentReplicationLinkJpaEntity existing = ContentReplicationLinkJpaEntity.create(
                "00000000-0000-0000-0000-000000000501", 21L, GlobalContentType.QUESTION,
                100L, "00000000-0000-0000-0000-000000000100", 3L,
                300L, "00000000-0000-0000-0000-000000000300", 7L, NOW);
        when(links.findByOrganizationIdAndContentTypeAndSourceGlobalContentIdAndSourceGlobalVersion(
                21L, GlobalContentType.QUESTION, 100L, 3L)).thenReturn(Optional.of(existing));

        var result = service.replicate(GlobalContentType.QUESTION, 100L, 3L, 21L, 7L, true);

        assertThat(result.existing()).isTrue();
        assertThat(result.internalId()).isEqualTo(300L);
        assertThat(result.publicId()).isEqualTo("00000000-0000-0000-0000-000000000300");
        assertThat(result.syncStatus()).isEqualTo(SyncStatus.SYNCHRONIZED);
        verify(resources, never()).copyToOrganization(any(), any(), any(), anyLong(), anyMap(), any(), anyBoolean());
        verify(links, never()).saveAndFlush(any());
    }

    @Test
    void createsAnOrganizationalCopyAndItsTraceabilityLink() {
        GlobalContentResourcePort resources = mock(GlobalContentResourcePort.class);
        GlobalContentVersionService versions = mock(GlobalContentVersionService.class);
        ContentReplicationLinkRepository links = mock(ContentReplicationLinkRepository.class);
        AuditLogPort audit = mock(AuditLogPort.class);
        ContentReplicationService service = new ContentReplicationService(resources, versions, links, audit, CLOCK);

        ContentResource global = resource(100L, "00000000-0000-0000-0000-000000000100",
                ContentScope.GLOBAL, 1L);
        ContentResource target = resource(300L, "00000000-0000-0000-0000-000000000300",
                ContentScope.ORGANIZATION, 21L);
        when(links.findByOrganizationIdAndContentTypeAndSourceGlobalContentIdAndSourceGlobalVersion(
                21L, GlobalContentType.QUESTION, 100L, 3L)).thenReturn(Optional.empty());
        when(resources.findByInternalId(GlobalContentType.QUESTION, 100L)).thenReturn(global);
        when(versions.isPublished(GlobalContentType.QUESTION, 100L, 3L)).thenReturn(true);
        when(resources.dependencies(GlobalContentType.QUESTION, 100L)).thenReturn(List.of());
        when(resources.copyToOrganization(GlobalContentType.QUESTION, 100L, 21L, 3L,
                Map.of(), 7L, true)).thenReturn(target);
        when(links.saveAndFlush(any(ContentReplicationLinkJpaEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.replicate(GlobalContentType.QUESTION, 100L, 3L, 21L, 7L, true);

        assertThat(result.existing()).isFalse();
        assertThat(result.internalId()).isEqualTo(300L);
        assertThat(result.syncStatus()).isEqualTo(SyncStatus.SYNCHRONIZED);
        verify(links).saveAndFlush(any(ContentReplicationLinkJpaEntity.class));
        verify(audit).record(any(), any(), any(), any(), any(), any(), any(), any());
    }

    private static ContentResource resource(Long id, String publicId, ContentScope scope, Long organizationId) {
        return new ContentResource(GlobalContentType.QUESTION, id, publicId, "Pregunta", "Descripción", "ACTIVE",
                scope, organizationId, null, null, 5L, NOW, 5L, NOW, 3L, false, false, "hash");
    }
}
