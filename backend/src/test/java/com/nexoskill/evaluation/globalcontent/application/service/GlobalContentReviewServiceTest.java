package com.nexoskill.evaluation.globalcontent.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.globalcontent.application.model.GlobalContentModels.ReviewFilter;
import com.nexoskill.evaluation.globalcontent.application.model.GlobalContentModels.ReviewPage;
import com.nexoskill.evaluation.globalcontent.application.port.out.GlobalContentResourcePort;
import com.nexoskill.evaluation.organizations.domain.model.ContentScope;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class GlobalContentReviewServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-02T05:00:00Z"), ZoneOffset.UTC);

    @Test
    void rejectsPageSizesOutsideTheHomogeneousContract() {
        GlobalContentResourcePort resources = mock(GlobalContentResourcePort.class);
        GlobalContentReviewService service = new GlobalContentReviewService(
                resources, mock(AuditLogPort.class), CLOCK);
        ReviewFilter filter = new ReviewFilter(null, null, null, ContentScope.GLOBAL,
                null, null, null, null, null, null, 0, 20);

        assertThatThrownBy(() -> service.review(filter, 7L))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("PAGINATION_SIZE_INVALID"));
        verify(resources, never()).review(any());
    }

    @Test
    void delegatesDatabasePaginationAndPreservesTotals() {
        GlobalContentResourcePort resources = mock(GlobalContentResourcePort.class);
        AuditLogPort audit = mock(AuditLogPort.class);
        GlobalContentReviewService service = new GlobalContentReviewService(resources, audit, CLOCK);
        ReviewFilter filter = new ReviewFilter(null, null, null, ContentScope.GLOBAL,
                null, null, null, null, null, null, 2, 25);
        ReviewPage expected = new ReviewPage(List.of(), 2, 25, 61, 3);
        when(resources.review(filter)).thenReturn(expected);

        ReviewPage result = service.review(filter, 7L);

        assertThat(result).isSameAs(expected);
        verify(resources).review(filter);
        verify(audit).record(any(), any(), any(), any(), any(), any(), any(), any());
    }
}
