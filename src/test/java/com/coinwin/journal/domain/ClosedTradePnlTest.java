package com.coinwin.journal.domain;

import static com.coinwin.journal.JournalFixtures.EXIT_AT;
import static com.coinwin.journal.JournalFixtures.PLANNED_AT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import com.coinwin.journal.JournalFixtures;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * 실현 손익과 반사실 손실. 기댓값은 전부 손으로 검산된다 — 평단 59500, 수량 0.1.
 */
class ClosedTradePnlTest {

    @Test
    void 롱_총손익은_청산가와_평단의_차이에_수량을_곱한_값이다() {
        assertThat(JournalFixtures.closedAtTarget().grossPnl())
                .isEqualTo(Money.of("450.00"));
    }

    @Test
    void 실현_손익은_총손익에서_수수료와_펀딩비를_뺀_값이다() {
        assertThat(JournalFixtures.closedAtTarget().realizedPnl())
                .isEqualTo(Money.of("443.80"));
    }

    /** 숏은 부호가 뒤집힌다. 평단 60500, 청산 56000 → 이익이다. */
    @Test
    void 숏은_가격이_내려야_이익이다() {
        ClosedTrade shortTrade = shortTradeClosedAt("56000");

        assertThat(shortTrade.grossPnl()).isEqualTo(Money.of("450.00"));
    }

    @Test
    void 숏은_가격이_오르면_손실이다() {
        ClosedTrade shortTrade = shortTradeClosedAt("62000");

        assertThat(shortTrade.grossPnl()).isEqualTo(Money.of("-150.00"));
        assertThat(shortTrade.realizedPnl().isNegative()).isTrue();
    }

    /** 절대값을 쓰면 손실과 이익이 같은 값이 된다. 손익은 부호가 전부다. */
    @Test
    void 롱이_손절가에서_닫히면_손익은_음수다() {
        assertThat(JournalFixtures.closedAtStop().grossPnl()).isEqualTo(Money.of("-150.00"));
        assertThat(JournalFixtures.closedAtStop().realizedPnl()).isEqualTo(Money.of("-155.00"));
    }

    @Test
    void 반사실_손실은_손절가에_전량_닫았을_때의_손익에서_수수료만_뺀다() {
        assertThat(JournalFixtures.closedAtTarget().lossIfStopHonored())
                .isEqualTo(Money.of("-155.00"));
    }

    /**
     * 펀딩비를 반사실에 넣지 않는 이유가 이 테스트다. 같은 계획·같은 체결이면 반사실은
     * 청산가와 펀딩비가 무엇이든 같은 값이어야 한다 — 손절을 지켰다면 벌어졌을 일이기 때문이다.
     */
    @Test
    void 반사실_손실은_실제_청산가와_보유_기간에_좌우되지_않는다() {
        assertThat(JournalFixtures.heldPastStop().lossIfStopHonored())
                .isEqualTo(JournalFixtures.closedAtStop().lossIfStopHonored());
    }

    @Test
    void 손절을_지나쳐_들고_있었으면_어긴_대가가_음수로_나온다() {
        ClosedTrade held = JournalFixtures.heldPastStop();

        assertThat(held.realizedPnl()).isEqualTo(Money.of("-258.00"));
        assertThat(held.costOfDeviation()).isEqualTo(Money.of("-103.00"));
        assertThat(held.costOfDeviation().isNegative()).isTrue();
    }

    /** 계획대로 손절된 거래에서 어긴 대가는 펀딩비만큼만 남는다. */
    @Test
    void 계획대로_손절된_거래의_어긴_대가는_펀딩비뿐이다() {
        assertThat(JournalFixtures.closedAtStop().costOfDeviation()).isEqualTo(Money.of("0.00"));
    }

    @Test
    void 계획_준수_여부는_청산_이유가_정한다() {
        assertThat(JournalFixtures.closedAtTarget().followedPlan()).isTrue();
        assertThat(JournalFixtures.closedAtStop().followedPlan()).isTrue();
        assertThat(JournalFixtures.heldPastStop().followedPlan()).isFalse();
    }

    /** 이익이어도 계획을 어긴 거래일 수 있다. 두 축을 분리하는 것이 이 모듈의 목적이다. */
    @Test
    void 이익이면서_계획을_어긴_거래가_존재한다() {
        ClosedTrade earlyWin = JournalFixtures.closedAt(
                Price.of("61000"), ExitReason.MANUAL_EARLY, EXIT_AT);

        assertThat(earlyWin.realizedPnl().isNegative()).isFalse();
        assertThat(earlyWin.followedPlan()).isFalse();
    }

    @Test
    void 직전_거래의_청산과_이_거래의_진입_사이_간격을_낸다() {
        ClosedTrade previous = JournalFixtures.closedEndingAt(
                PLANNED_AT.minus(Duration.ofHours(3)), ExitReason.PLANNED_TARGET);

        assertThat(JournalFixtures.closedAtTarget().timeSincePreviousTrade(previous))
                .isEqualTo(Duration.ofHours(4));
    }

    @Test
    void 직전_거래가_이_거래보다_늦게_닫혔으면_거부한다() {
        ClosedTrade later = JournalFixtures.closedEndingAt(
                EXIT_AT.plus(Duration.ofDays(1)), ExitReason.PLANNED_TARGET);
        ClosedTrade earlier = JournalFixtures.closedAtTarget();

        assertThatThrownBy(() -> earlier.timeSincePreviousTrade(later))
                .isInstanceOf(InvalidTradeException.class)
                .hasMessageContaining("직전 거래는 이 거래가 열리기 전에 닫혀 있어야 한다");
    }

    private static ClosedTrade shortTradeClosedAt(String exitPrice) {
        return PlannedTrade.of(JournalFixtures.shortPlan(), PLANNED_AT)
                .fill(ExecutedEntries.of(
                                new Fill(Price.of("60000"), Quantity.of("0.05"),
                                        JournalFixtures.FIRST_FILL_AT),
                                new Fill(Price.of("61000"), Quantity.of("0.05"),
                                        JournalFixtures.SECOND_FILL_AT)),
                        JournalFixtures.context())
                .close(new TradeClosure(new Exit(Price.of(exitPrice), EXIT_AT),
                        ExitReason.PLANNED_TARGET, TradeCosts.of("5.00", "1.20")));
    }
}
