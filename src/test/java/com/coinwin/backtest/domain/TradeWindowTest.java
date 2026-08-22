package com.coinwin.backtest.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.coinwin.common.domain.InvalidValueException;
import com.coinwin.common.domain.Money;
import com.coinwin.journal.JournalFixtures;
import com.coinwin.journal.domain.ClosedTrade;
import com.coinwin.journal.domain.ExitReason;
import com.coinwin.market.domain.TimeRange;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 구간별 성적의 규칙.
 *
 * <p>픽스처의 거래는 청산 시각 8시간 전에 진입한다({@code JournalFixtures.closedEndingAt}).
 * 진입 시각으로 가른다는 규칙이 실제로 지켜지는지는 그 8시간 차이로 확인한다.
 */
class TradeWindowTest {

    private static final Instant BASE = Instant.parse("2026-01-01T00:00:00Z");
    private static final Money CAPITAL = Money.of("800");

    @Test
    void 구간에_진입한_거래만_센다() {
        ClosedTrade inside = winnerExitingAt(BASE.plus(Duration.ofDays(1)));
        ClosedTrade outside = winnerExitingAt(BASE.plus(Duration.ofDays(40)));

        TradeWindow window = TradeWindow.enteredWithin(
                januaryFirstWeek(), List.of(inside, outside), CAPITAL);

        assertThat(window.size()).isEqualTo(1);
        assertThat(window.trades()).containsExactly(inside);
    }

    /**
     * 진입은 구간 안, 청산은 구간 밖인 거래는 <b>포함한다.</b> 조합을 고른 시점 이후에 시작된
     * 거래가 그 조합의 성적이기 때문이다. 청산 시각으로 가르면 시작하지도 않은 구간의 성적에
     * 거래가 들어간다.
     */
    @Test
    void 구간_경계를_걸친_거래는_진입_시각이_속한_구간에_들어간다() {
        // 진입은 청산 8시간 전이다. 구간 끝을 4시간 넘겨 닫으면 진입만 구간 안에 남는다.
        Instant exitJustAfterWindow = BASE.plus(Duration.ofDays(7)).plus(Duration.ofHours(4));
        ClosedTrade straddling = winnerExitingAt(exitJustAfterWindow);

        TimeRange firstWeek = januaryFirstWeek();
        assertThat(firstWeek.contains(straddling.openedAt())).isTrue();
        assertThat(firstWeek.contains(straddling.closedAt())).isFalse();

        assertThat(TradeWindow.enteredWithin(firstWeek, List.of(straddling), CAPITAL).size())
                .isEqualTo(1);
    }

    /** 구간은 반열림 {@code [from, to)} 다. {@link TimeRange} 의 정의를 그대로 따른다. */
    @Test
    void 구간_시작에_진입한_거래는_포함하고_끝에_진입한_거래는_제외한다() {
        Instant from = BASE;
        Instant to = BASE.plus(Duration.ofDays(7));
        ClosedTrade atStart = winnerExitingAt(from.plus(Duration.ofHours(8)));
        ClosedTrade atEnd = winnerExitingAt(to.plus(Duration.ofHours(8)));

        assertThat(atStart.openedAt()).isEqualTo(from);
        assertThat(atEnd.openedAt()).isEqualTo(to);

        TradeWindow window = TradeWindow.enteredWithin(
                new TimeRange(from, to), List.of(atStart, atEnd), CAPITAL);

        assertThat(window.trades()).containsExactly(atStart);
    }

    /**
     * 거래가 없는 구간은 <b>성적이 0 인 구간이 아니라 성적을 말할 수 없는 구간</b>이다.
     * 건수 0 과 손익 0 은 함께 나오므로 표에서 구분된다.
     */
    @Test
    void 거래가_없는_구간은_손익비를_말할_수_없다() {
        TradeWindow empty = TradeWindow.enteredWithin(
                januaryFirstWeek(), List.of(), CAPITAL);

        assertThat(empty.size()).isZero();
        assertThat(empty.tally().realizedPnl()).isEqualTo(Money.of("0"));
        assertThat(empty.profitFactor()).isEmpty();
        assertThat(empty.equity().finalEquity()).isEqualTo(CAPITAL);
    }

    /**
     * 진 거래가 하나도 없으면 손익비는 비어 있다. 무한대나 큰 수를 돌려주면 표시하는 쪽이
     * 그것을 실제 성적으로 읽는다 — {@code BacktestResult} 가 갖고 있던 규칙이고, 이제
     * 구현이 여기 하나뿐이다.
     */
    @Test
    void 진_거래가_없으면_손익비를_말할_수_없다() {
        TradeWindow allWinners = new TradeWindow(
                List.of(winnerExitingAt(BASE.plus(Duration.ofDays(1)))), CAPITAL);

        assertThat(allWinners.profitFactor()).isEmpty();
    }

    @Test
    void 손익비는_총이익을_총손실의_절대값으로_나눈다() {
        ClosedTrade winner = winnerExitingAt(BASE.plus(Duration.ofDays(1)));
        ClosedTrade loser = loserExitingAt(BASE.plus(Duration.ofDays(2)));

        TradeWindow window = new TradeWindow(List.of(winner, loser), CAPITAL);

        assertThat(winner.realizedPnl()).isEqualTo(Money.of("443.80"));
        assertThat(loser.realizedPnl()).isEqualTo(Money.of("-156.20"));
        assertThat(window.profitFactor())
                .hasValueSatisfying(factor ->
                        assertThat(factor.doubleValue()).isCloseTo(2.8412, within(0.0005)));
    }

    /** 자산 곡선의 첫 점은 거래 이전의 시작 자본이다. 그래야 낙폭이 첫 거래부터 잡힌다. */
    @Test
    void 자산_곡선은_시작_자본에서_출발해_시간순으로_손익을_더한다() {
        ClosedTrade loser = loserExitingAt(BASE.plus(Duration.ofDays(1)));
        ClosedTrade winner = winnerExitingAt(BASE.plus(Duration.ofDays(2)));

        TradeWindow window = new TradeWindow(List.of(loser, winner), CAPITAL);

        assertThat(window.equity().points()).containsExactly(
                Money.of("800"), Money.of("643.80"), Money.of("1087.60"));
        assertThat(window.equity().finalEquity()).isEqualTo(Money.of("1087.60"));
    }

    /**
     * 같은 두 거래라도 순서가 바뀌면 낙폭이 달라진다. 먼저 지고 나중에 이긴 계좌와 먼저 이기고
     * 나중에 진 계좌는 다른 화면을 본다 — {@code EquityCurve} 가 있는 이유 그 자체다.
     */
    @Test
    void 낙폭은_거래_순서에_의존한다() {
        ClosedTrade loser = loserExitingAt(BASE.plus(Duration.ofDays(1)));
        ClosedTrade winner = winnerExitingAt(BASE.plus(Duration.ofDays(2)));

        assertThat(new TradeWindow(List.of(loser, winner), CAPITAL).maxDrawdown())
                .isNotEqualTo(new TradeWindow(List.of(winner, loser), CAPITAL).maxDrawdown());
    }

    @Test
    void 시작_자본_없이는_만들_수_없다() {
        assertThatThrownBy(() -> new TradeWindow(List.of(), null))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    void 구간_없이는_거를_수_없다() {
        assertThatThrownBy(() -> TradeWindow.enteredWithin(null, List.of(), CAPITAL))
                .isInstanceOf(InvalidValueException.class);
    }

    private static TimeRange januaryFirstWeek() {
        return new TimeRange(BASE, BASE.plus(Duration.ofDays(7)));
    }

    /** 익절 64000 → 실현 손익 443.80. */
    private static ClosedTrade winnerExitingAt(Instant exitAt) {
        return JournalFixtures.closedEndingAt(exitAt, ExitReason.PLANNED_TARGET);
    }

    /** 손절 58000 → 실현 손익 -156.20. */
    private static ClosedTrade loserExitingAt(Instant exitAt) {
        return JournalFixtures.closedEndingAt(exitAt, ExitReason.PLANNED_STOP, "58000");
    }
}
