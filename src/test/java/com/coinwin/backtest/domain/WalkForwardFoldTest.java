package com.coinwin.backtest.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.InvalidValueException;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Percentage;
import com.coinwin.journal.JournalFixtures;
import com.coinwin.journal.domain.ClosedTrade;
import com.coinwin.journal.domain.ExitReason;
import com.coinwin.market.domain.CandleInterval;
import com.coinwin.market.domain.CandleQuery;
import com.coinwin.market.domain.Symbol;
import com.coinwin.market.domain.TimeRange;
import com.coinwin.projection.domain.EquityCurve;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 구간 밖 검증의 규칙.
 *
 * <p>후보는 <b>전체 시계열에서 이미 돌린 결과</b>다. 폴드가 하는 일은 그 결과를 두 구간으로
 * 가르고, 앞 구간만 보고 하나를 고른 뒤, 뒤 구간의 성적을 그대로 읽는 것뿐이다.
 *
 * <p>픽스처의 이긴 거래는 +443.80, 진 거래는 -156.20 이다({@code JournalFixtures}). 손익비는
 * 그 두 수의 개수비로만 정해지므로 기댓값이 암산된다.
 */
class WalkForwardFoldTest {

    private static final Instant BASE = Instant.parse("2026-01-01T00:00:00Z");
    private static final Money CAPITAL = Money.of("800");

    /** 표본이 얇은 것과 성적이 나쁜 것을 가르려면 최소 건수가 2 보다 커야 한다. */
    private static final int MIN_TRADES = 3;

    private static final TimeRange IN_SAMPLE =
            new TimeRange(BASE, BASE.plus(Duration.ofDays(30)));
    private static final TimeRange OUT_OF_SAMPLE =
            new TimeRange(BASE.plus(Duration.ofDays(30)), BASE.plus(Duration.ofDays(60)));

    // ── 구간의 모양 ─────────────────────────────────────────────────────────

    @Test
    void 검증_구간은_학습_구간_뒤에_있어야_한다() {
        assertThatThrownBy(() -> new WalkForwardFold(OUT_OF_SAMPLE, IN_SAMPLE, MIN_TRADES))
                .isInstanceOf(InvalidBacktestException.class)
                .hasMessageContaining("검증 구간");
    }

    /**
     * 한 순간이라도 겹치면 그 구간은 고르는 데도 쓰이고 검증하는 데도 쓰인다. 그 순간 이것은
     * 구간 밖 검증이 아니다.
     */
    @Test
    void 학습_구간과_검증_구간은_겹칠_수_없다() {
        TimeRange overlapping = new TimeRange(
                BASE.plus(Duration.ofDays(29)), BASE.plus(Duration.ofDays(60)));

        assertThatThrownBy(() -> new WalkForwardFold(IN_SAMPLE, overlapping, MIN_TRADES))
                .isInstanceOf(InvalidBacktestException.class)
                .hasMessageContaining("검증 구간");
    }

    @Test
    void 검증_구간이_학습_구간에_바로_이어지는_것은_허용한다() {
        assertThat(new WalkForwardFold(IN_SAMPLE, OUT_OF_SAMPLE, MIN_TRADES).outOfSample())
                .isEqualTo(OUT_OF_SAMPLE);
    }

    @Test
    void 최소_거래_수는_1_보다_작을_수_없다() {
        assertThatThrownBy(() -> new WalkForwardFold(IN_SAMPLE, OUT_OF_SAMPLE, 0))
                .isInstanceOf(InvalidValueException.class);
    }

    // ── 고르기 ──────────────────────────────────────────────────────────────

    @Test
    void 학습_구간_손익비가_가장_높은_후보를_고른다() {
        BacktestResult weak = candidate(inSample(1, 2), List.of());
        BacktestResult strong = candidate(inSample(3, 1), List.of());

        assertThat(fold().choose(List.of(weak, strong))).containsSame(strong);
    }

    /**
     * <b>12건은 통계가 아니다.</b> 학습 구간 표본이 얇은 조합을 고르는 것이 과최적화의 정의
     * 그 자체다 — 로드맵 Phase 6 이 남긴 문장을 규칙으로 올린 것이다.
     *
     * <p>얇은 쪽의 손익비가 <b>더 높은데도</b> 지는 것이 이 규칙의 요점이다.
     */
    @Test
    void 학습_구간_표본이_얇은_후보는_고르지_않는다() {
        BacktestResult thin = candidate(inSample(1, 1), List.of());
        BacktestResult thick = candidate(inSample(1, 3), List.of());

        assertThat(thin.window().profitFactor().orElseThrow())
                .isGreaterThan(thick.window().profitFactor().orElseThrow());
        assertThat(fold().choose(List.of(thin, thick))).containsSame(thick);
    }

    /**
     * 진 거래가 하나도 없으면 손익비를 말할 수 없고, 말할 수 없는 것끼리는 크기를 비교할 수
     * 없다. 무한대로 치면 그 후보가 언제나 이긴다 — 표본이 얇을수록 이긴다는 뜻이다.
     */
    @Test
    void 손익비를_말할_수_없는_후보는_고르지_않는다() {
        BacktestResult unbeaten = candidate(winnersFrom(1, 5), List.of());

        assertThat(unbeaten.window().size()).isGreaterThanOrEqualTo(MIN_TRADES);
        assertThat(unbeaten.window().profitFactor()).isEmpty();
        assertThat(fold().choose(List.of(unbeaten))).isEmpty();
    }

    @Test
    void 자격을_갖춘_후보가_하나도_없으면_고르지_못한다() {
        assertThat(fold().choose(List.of(candidate(inSample(1, 1), List.of())))).isEmpty();
    }

    @Test
    void 후보가_비어_있으면_고르지_못한다() {
        assertThat(fold().choose(List.of())).isEmpty();
    }

    /**
     * 동점에서 순서가 흔들리면 같은 입력이 같은 표를 내지 않는다. 완료 조건이 걸린 자리다.
     *
     * <p>두 후보의 건수를 다르게 둔 이유는, 내용이 같으면 record 의 동등성 때문에 어느 쪽이
     * 골라졌는지 구분되지 않아 이 테스트가 아무것도 증명하지 않기 때문이다.
     */
    @Test
    void 손익비가_같으면_먼저_온_후보를_고른다() {
        BacktestResult fewer = candidate(inSample(2, 2), List.of());
        BacktestResult more = candidate(inSample(4, 4), List.of());

        assertThat(fewer.window().profitFactor()).isEqualTo(more.window().profitFactor());
        assertThat(fold().choose(List.of(fewer, more))).containsSame(fewer);
        assertThat(fold().choose(List.of(more, fewer))).containsSame(more);
    }

    /** 검증 구간의 거래는 고르는 데 쓰이지 않는다. 이것이 이 절차의 정의다. */
    @Test
    void 검증_구간_성적은_고르는_데_쓰이지_않는다() {
        BacktestResult goodThenBad = candidate(inSample(3, 1), losersFrom(31, 4));
        BacktestResult badThenGood = candidate(inSample(1, 3), winnersFrom(31, 4));

        assertThat(fold().choose(List.of(goodThenBad, badThenGood))).containsSame(goodThenBad);
    }

    // ── 검증 ────────────────────────────────────────────────────────────────

    @Test
    void 검증_성적은_검증_구간에_진입한_거래만_본다() {
        BacktestResult result = candidate(inSample(3, 1), losersFrom(31, 2));

        assertThat(fold().inSampleOf(result).size()).isEqualTo(4);
        assertThat(fold().outOfSampleOf(result).size()).isEqualTo(2);
    }

    @Test
    void 검증_구간에_거래가_없으면_빈_성적이다() {
        BacktestResult result = candidate(inSample(3, 1), List.of());

        assertThat(fold().outOfSampleOf(result).isEmpty()).isTrue();
        assertThat(fold().outOfSampleOf(result).profitFactor()).isEmpty();
    }

    /** 두 창 모두 시작 자본을 스펙에서 읽는다. 구간마다 다른 자본으로 재면 낙폭이 갈린다. */
    @Test
    void 두_구간_모두_같은_시작_자본에서_잰다() {
        BacktestResult result = candidate(inSample(1, 1), losersFrom(31, 1));

        assertThat(fold().inSampleOf(result).startingEquity()).isEqualTo(CAPITAL);
        assertThat(fold().outOfSampleOf(result).startingEquity()).isEqualTo(CAPITAL);
    }

    // ── 픽스처 ──────────────────────────────────────────────────────────────

    private static WalkForwardFold fold() {
        return new WalkForwardFold(IN_SAMPLE, OUT_OF_SAMPLE, MIN_TRADES);
    }

    /** 학습 구간에 이긴 거래 {@code wins} 건과 진 거래 {@code losses} 건. */
    private static List<ClosedTrade> inSample(int wins, int losses) {
        List<ClosedTrade> trades = new ArrayList<>(winnersFrom(1, wins));
        trades.addAll(losersFrom(15, losses));
        return trades;
    }

    private static List<ClosedTrade> winnersFrom(int startDay, int count) {
        return series(startDay, count, ExitReason.PLANNED_TARGET, "64000");
    }

    private static List<ClosedTrade> losersFrom(int startDay, int count) {
        return series(startDay, count, ExitReason.PLANNED_STOP, "58000");
    }

    private static List<ClosedTrade> series(
            int startDay, int count, ExitReason reason, String exitPrice) {
        List<ClosedTrade> trades = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            trades.add(JournalFixtures.closedEndingAt(
                    BASE.plus(Duration.ofDays(startDay + i)), reason, exitPrice));
        }
        return trades;
    }

    private static BacktestResult candidate(
            List<ClosedTrade> inSample, List<ClosedTrade> outOfSample) {
        List<ClosedTrade> all = new ArrayList<>(inSample);
        all.addAll(outOfSample);
        return new BacktestResult(spec(), all, new EquityCurve(List.of(CAPITAL)));
    }

    private static BacktestSpec spec() {
        return new BacktestSpec(
                new CandleQuery(Symbol.BTC_USDT, CandleInterval.FOUR_HOURS,
                        new TimeRange(BASE, BASE.plus(Duration.ofDays(60)))),
                new StrategySettings(ZoneSettings.standard(),
                        new EntryRules(BigDecimal.ONE, new BigDecimal("1.5"), false)),
                new AccountSettings(CAPITAL, Percentage.of("2"), 10, CapitalMode.FIXED),
                new CostModel(Percentage.of("0.02"), Percentage.of("0.05"),
                        Percentage.of("0.02")));
    }
}
