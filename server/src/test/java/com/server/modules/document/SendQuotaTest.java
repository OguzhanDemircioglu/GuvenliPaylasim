package com.server.modules.document;

import com.server.common.exception.BusinessException;
import com.server.modules.invite.InviteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/** Crypto_Design §7.9: günde 200 kutu, aynı anda yaşayan gönderilmiş kutular (bekleyen davetler dahil) en fazla 500 MB. */
@ExtendWith(MockitoExtension.class)
class SendQuotaTest {

    private static final Long SENDER = 1L;
    private static final long MB = 1024 * 1024;

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private InviteRepository inviteRepository;

    private SendQuota quota;

    @BeforeEach
    void setUp() {
        quota = new SendQuota(documentRepository, inviteRepository);
    }

    @Test
    void underTheLimitsPasses() {
        when(documentRepository.countBySenderIdAndCreatedAtAfter(eq(SENDER), any())).thenReturn(199L);
        when(documentRepository.sumLiveSize(eq(SENDER), any())).thenReturn(400 * MB);
        when(inviteRepository.sumPendingSize(eq(SENDER), any())).thenReturn(90 * MB);

        assertThatCode(() -> quota.check(SENDER, 10 * MB)).doesNotThrowAnyException();
    }

    @Test
    void dailyLimit() {
        when(documentRepository.countBySenderIdAndCreatedAtAfter(eq(SENDER), any())).thenReturn(200L);

        assertThatThrownBy(() -> quota.check(SENDER, 1)).isInstanceOf(BusinessException.class)
                .hasMessage(DocumentMessages.DAILY_LIMIT);
    }

    @Test
    void storageLimitCountsLiveBoxesPendingInvitesAndTheNewBox() {
        when(documentRepository.countBySenderIdAndCreatedAtAfter(eq(SENDER), any())).thenReturn(0L);
        when(documentRepository.sumLiveSize(eq(SENDER), any())).thenReturn(400 * MB);
        when(inviteRepository.sumPendingSize(eq(SENDER), any())).thenReturn(95 * MB);

        assertThatThrownBy(() -> quota.check(SENDER, 6 * MB)).hasMessage(DocumentMessages.QUOTA_EXCEEDED);
    }
}
