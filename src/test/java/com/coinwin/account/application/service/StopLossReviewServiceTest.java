package com.coinwin.account.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.account.AccountFixtures;
import com.coinwin.account.adapter.out.memory.InMemoryOpenOrderAdapter;
import com.coinwin.account.application.port.in.ReconcilePositionsUseCase;
import com.coinwin.account.domain.PositionProtectionReview;
import com.coinwin.account.domain.PositionReconciliation;
import com.coinwin.account.domain.ProtectiveOrder;
import com.coinwin.account.domain.StopLossCoverage;
import com.coinwin.common.domain.ExternalDataUnavailableException;
import com.coinwin.journal.JournalFixtures;
import com.coinwin.position.domain.Direction;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * 서비스의 조율 규칙. <b>키도 DB 도 없이 전부 돈다</b> — 인메모리 어댑터와 대조 스텁만 쓴다.
 */
class StopLossReviewServiceTest {

    private static final Instant AT = AccountFixtures.OBSERVED_AT;

    @Test
    void 손절이_없으면_보호되지_않은_것으로_낸다() {
        PositionProtectionReview review = review();

        assertThat(review.allProtected()).isFalse();
        assertThat(review.unprotected()).singleElement()
                .satisfies(protection -> assertThat(protection.coverage())
                        .isEqualTo(StopLossCoverage.NONE));
    }

    @Test
    void 전량_손절이_걸려_있으면_손댈_것이_없다() {
        assertThat(review(AccountFixtures.stop(Direction.LONG, "58000")).allProtected()).isTrue();
    }

    /**
     * <b>폴백을 두지 않는다.</b> 키가 없을 때 인메모리 어댑터를 대신 올리면 "손절이 걸려 있다"
     * 는 거짓말이 화면에 뜬다. 비어 있는 것과 알 수 없는 것은 다른 사실이고, 이 기능은 정확히
     * 그 구분을 위해 있다.
     */
    @Test
    void 거래소가_연결되지_않으면_알_수_없다고_말한다() {
        StopLossReviewService service =
                new StopLossReviewService(new StubReconcile(), Optional.empty());

        assertThatThrownBy(service::review)
                .isInstanceOf(ExternalDataUnavailableException.class)
                .hasMessageContaining("COINWIN_ACCOUNT_BINANCE_API_KEY");
    }

    /** 대조를 다시 짜지 않는다. 방향 짝짓기와 "미청산이 무엇인가" 는 그쪽의 정책이다. */
    @Test
    void 관측_시각은_대조에서_온다() {
        assertThat(review().observedAt()).isEqualTo(AT);
    }

    private static PositionProtectionReview review(ProtectiveOrder... orders) {
        return new StopLossReviewService(
                new StubReconcile(), Optional.of(new InMemoryOpenOrderAdapter(orders))).review();
    }

    /** 롱 기록 한 건과 같은 방향 거래소 포지션 하나를 짝지어 둔 대조. */
    private record StubReconcile() implements ReconcilePositionsUseCase {

        @Override
        public PositionReconciliation reconcile() {
            return PositionReconciliation.of(
                    List.of(JournalFixtures.open()),
                    List.of(AccountFixtures.longPosition("0.1", AT)), AT);
        }
    }
}
