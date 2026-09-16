package com.coinwin.account.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.coinwin.account.AccountFixtures;
import com.coinwin.common.domain.Price;
import com.coinwin.journal.JournalFixtures;
import com.coinwin.journal.domain.OpenTrade;
import com.coinwin.position.domain.Direction;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 대조 결과 위에 미체결 주문을 얹는 규칙. */
class PositionProtectionReviewTest {

    private static final Instant AT = AccountFixtures.OBSERVED_AT;

    @Test
    void 손절이_없는_포지션을_골라낸다() {
        PositionProtectionReview review = review(List.of());

        assertThat(review.allProtected()).isFalse();
        assertThat(review.unprotected()).singleElement()
                .extracting(PositionProtection::coverage).isEqualTo(StopLossCoverage.NONE);
    }

    @Test
    void 전량이_덮여_있으면_손댈_것이_없다() {
        PositionProtectionReview review =
                review(List.of(AccountFixtures.stop(Direction.LONG, "58000")));

        assertThat(review.allProtected()).isTrue();
        assertThat(review.unprotected()).isEmpty();
    }

    /**
     * <b>있어야 할 손절가는 기록의 계획에서만 온다.</b> 손절 거리의 기본값은 이 저장소가 아직
     * 재지 않았고({@code exit-automation.md} § 4), 재지 않은 수를 화면에 놓으면 사람이 그것을
     * 기준으로 읽는다.
     */
    @Test
    void 있어야_할_손절가는_기록된_계획에서_온다() {
        PositionProtectionReview review = review(List.of());

        assertThat(review.protections()).singleElement()
                .extracting(PositionProtection::plannedStopLoss)
                .isEqualTo(java.util.Optional.of(Price.of("58000")));
    }

    /** 앱 밖에서 연 포지션은 계획이 없다. 지어내지 않고 비워 둔다. */
    @Test
    void 기록이_없는_포지션은_있어야_할_손절가를_말할_수_없다() {
        PositionProtectionReview review = PositionProtectionReview.of(
                PositionReconciliation.of(
                        List.of(), List.of(AccountFixtures.longPosition("0.1", AT)), AT),
                List.of());

        assertThat(review.protections()).singleElement()
                .extracting(PositionProtection::plannedStopLoss).isEqualTo(java.util.Optional.empty());
    }

    /**
     * <b>기록에만 있는 거래는 여기 없다.</b> 거래소에 포지션이 없으면 걸 손절도 없고, 그쪽은
     * 대조가 이미 말한다 — 두 화면이 같은 사실에 서로 다른 경고를 내면 안 된다.
     */
    @Test
    void 거래소에_없는_기록은_보호_판정에_들어가지_않는다() {
        PositionProtectionReview review = PositionProtectionReview.of(
                PositionReconciliation.of(List.of(JournalFixtures.open()), List.of(), AT),
                List.of());

        assertThat(review.protections()).isEmpty();
        assertThat(review.allProtected()).isTrue();
    }

    /** 포지션이 하나도 없는 것은 규칙을 어긴 상태가 아니다. */
    @Test
    void 포지션이_없으면_전부_보호된_것이다() {
        PositionProtectionReview review = PositionProtectionReview.of(
                PositionReconciliation.of(List.of(), List.of(), AT), List.of());

        assertThat(review.allProtected()).isTrue();
    }

    @Test
    void 관측_시각은_대조가_말한_것을_쓴다() {
        assertThat(review(List.of()).observedAt()).isEqualTo(AT);
    }

    /** 롱 기록(계획 손절 58000) 한 건과 같은 방향 거래소 포지션 하나. */
    private static PositionProtectionReview review(List<ProtectiveOrder> orders) {
        OpenTrade recorded = JournalFixtures.open();
        return PositionProtectionReview.of(
                PositionReconciliation.of(
                        List.of(recorded), List.of(AccountFixtures.longPosition("0.1", AT)), AT),
                orders);
    }
}
