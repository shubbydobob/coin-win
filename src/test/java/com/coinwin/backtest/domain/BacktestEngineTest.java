package com.coinwin.backtest.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.backtest.BacktestFixtures;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Percentage;
import com.coinwin.indicator.domain.InsufficientCandlesException;
import com.coinwin.journal.domain.ClosedTrade;
import com.coinwin.journal.domain.ExitReason;
import com.coinwin.market.domain.CandleInterval;
import com.coinwin.market.domain.CandleQuery;
import com.coinwin.market.domain.CandleSeries;
import com.coinwin.market.domain.Symbol;
import com.coinwin.market.domain.TimeRange;
import com.coinwin.position.domain.FixedMaintenanceMarginPolicy;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class BacktestEngineTest {

    private static final BacktestEngine ENGINE =
            new BacktestEngine(new FixedMaintenanceMarginPolicy(Percentage.of("0.4")));

    private static final CandleQuery QUERY = new CandleQuery(
            new Symbol("BTCUSDT"), CandleInterval.ONE_HOUR,
            new TimeRange(BacktestFixtures.T0, BacktestFixtures.T0.plus(Duration.ofDays(60))));

    /** 240봉. 일목 워밍업 77봉을 넘기고 대가 여러 번 형성된다. */
    private static final CandleSeries SERIES = BacktestFixtures.zigzag(30, 8, 59000, 61000);

    private static BacktestSpec spec() {
        return new BacktestSpec(QUERY,
                new StrategySettings(ZoneSettings.standard(),
                        new EntryRules(BigDecimal.ONE, new BigDecimal("1.5"), false)),
                new AccountSettings(Money.of("800"), Percentage.of("2"), 10, CapitalMode.FIXED),
                CostModel.binanceDefaults());
    }

    private static BacktestResult run(BacktestSpec spec) {
        return ENGINE.run(spec, SERIES);
    }

    /**
     * 나머지 테스트가 공허하지 않다는 전제. 거래가 0 건이면 결정론도 룩어헤드도 자동으로
     * 통과한다.
     */
    @Test
    void 톱니_시리즈에서_거래가_실제로_발생한다() {
        assertThat(run(spec()).totalTrades()).isPositive();
    }

    // ── 완료 조건 ───────────────────────────────────────────────────────────

    /**
     * "동일 파라미터 재실행 시 결과 완전 동일" — roadmap.md 의 Phase 6 완료 조건.
     *
     * <p>결과 전체를 {@code equals} 로 비교한다. 지표 요약만 맞대면 체결 하나가 달라도 통과한다.
     * 난수를 쓰지 않으므로 시드 고정 항목은 없고, 유일한 위험이던 거래 식별자는 진입 시각에서
     * 유도한다.
     */
    @Test
    void 동일한_스펙을_두_번_실행하면_결과가_완전히_같다() {
        assertThat(run(spec())).isEqualTo(run(spec()));
    }

    /**
     * 룩어헤드 방지. 뒤에서 K봉을 떼고 돌려도 <b>겹치는 구간의 거래가 같아야 한다.</b>
     *
     * <p>미래를 한 번이라도 참조하면 앞 구간의 판단이 달라진다. 피벗 확정 지연을 빠뜨렸거나
     * 후행스팬을 썼거나 인덱스를 한 칸 밀었거나 — 원인이 무엇이든 여기서 잡힌다. 특정 케이스가
     * 아니라 <b>모든 입력에 대해</b> 성립해야 하는 성질이라 개별 테스트보다 강하다.
     */
    @Test
    void 캔들을_뒤에서_잘라도_남은_구간의_거래가_동일하다() {
        List<ClosedTrade> full = run(spec()).trades();

        CandleSeries truncated = new CandleSeries(
                SERIES.candles().subList(0, SERIES.size() - 40));
        List<ClosedTrade> partial = ENGINE.run(spec(), truncated).trades();

        assertThat(partial).isNotEmpty();
        assertThat(full.subList(0, partial.size())).isEqualTo(partial);
    }

    // ── 불변식 ──────────────────────────────────────────────────────────────

    /** 계획을 어길 방법이 없다. 사람이 개입하지 않으므로 손절과 익절 둘뿐이다. */
    @Test
    void 모든_청산은_계획된_손절_아니면_익절이다() {
        assertThat(run(spec()).trades()).extracting(trade -> trade.closure().reason())
                .containsAnyOf(ExitReason.PLANNED_STOP, ExitReason.PLANNED_TARGET)
                .allSatisfy(reason -> assertThat(reason)
                        .isIn(ExitReason.PLANNED_STOP, ExitReason.PLANNED_TARGET));
    }

    /** 동시 포지션 1개. 겹치는 거래가 하나라도 있으면 {@code overlaps} 가 0 이 아니다. */
    @Test
    void 거래는_겹치지_않는다() {
        assertThat(run(spec()).journal().intervals().overlaps()).isZero();
    }

    @Test
    void 자산_곡선의_점은_거래_수보다_하나_많다() {
        BacktestResult result = run(spec());

        assertThat(result.equity().trades()).isEqualTo(result.totalTrades());
        assertThat(result.equity().initialCapital()).isEqualTo(Money.of("800"));
        assertThat(result.finalEquity())
                .isEqualTo(Money.of("800").plus(result.netPnl()));
    }

    /** 백테스트는 계획을 어기지 않으므로 준수율이 100% 이고 반사실 손실이 0 이다. */
    @Test
    void 모든_거래가_계획을_따른다() {
        assertThat(run(spec()).journal().planAdherence()).isEqualTo(Percentage.of("100"));
    }

    // ── 비교 실행 ───────────────────────────────────────────────────────────

    /** 수수료가 엣지를 먹어 치우는지를 수치로 본다. 같은 체결에 비용만 다르다. */
    @Test
    void 비용을_끄면_같은_거래에서_손익이_더_좋다() {
        BacktestResult paid = run(spec());
        BacktestResult free = run(spec().withCosts(CostModel.free()));

        assertThat(free.totalTrades()).isEqualTo(paid.totalTrades());
        assertThat(free.netPnl().value()).isGreaterThan(paid.netPnl().value());
    }

    /** 필터를 켜면 신호가 줄어든다. 늘어날 수는 없다 — 게이트는 통과만 막는다. */
    @Test
    void 지표_필터를_켜면_거래가_줄거나_같다() {
        BacktestResult off = run(spec().withIndicatorFilter(false));
        BacktestResult on = run(spec().withIndicatorFilter(true));

        assertThat(on.totalTrades()).isLessThanOrEqualTo(off.totalTrades());
    }

    /**
     * 고정과 복리는 <b>같은 체결에 다른 수량</b>을 쓴다. 거래 목록의 길이와 진입 시각은 같고
     * 손익만 갈려야 한다 — 갈리는 지점이 사이징 하나뿐임을 확인한다.
     */
    @Test
    void 복리와_고정은_체결은_같고_손익만_다르다() {
        BacktestResult fixed = run(spec());
        BacktestResult compound = run(spec().withCapitalMode(CapitalMode.COMPOUND));

        assertThat(compound.trades()).extracting(ClosedTrade::openedAt)
                .isEqualTo(fixed.trades().stream().map(ClosedTrade::openedAt).toList());
        assertThat(compound.finalEquity()).isNotEqualTo(fixed.finalEquity());
    }

    /**
     * 순수한 톱니는 가격이 반드시 되돌아오므로 <b>진 거래가 하나도 없다.</b> 손실이 없는 표본은
     * 손익비를 말할 수 없는 표본이다 — 무한대나 큰 수를 돌려주면 표시하는 쪽이 그것을 실제
     * 성적으로 읽는다.
     */
    @Test
    void 되돌림이_확실한_시리즈에서는_손익비를_말할_수_없다() {
        BacktestResult result = run(spec());

        assertThat(result.winRate()).isEqualTo(result.tally().winRate());
        assertThat(result.tally().losses()).isZero();
        assertThat(result.profitFactor()).isEmpty();
    }

    /** 지지대가 뚫리면 손절이 나온다. 그때 비로소 손익비와 낙폭이 뜻을 갖는다. */
    @Test
    void 지지대가_뚫리는_시리즈에서는_손절과_낙폭이_나온다() {
        BacktestResult result = ENGINE.run(spec(),
                BacktestFixtures.zigzagThenBreakdown(SERIES, 40));

        assertThat(result.trades()).extracting(trade -> trade.closure().reason())
                .contains(ExitReason.PLANNED_STOP);
        assertThat(result.tally().losses()).isPositive();
        assertThat(result.profitFactor()).isPresent();
        assertThat(result.maxDrawdown().value().signum()).isPositive();
    }

    /**
     * 워밍업을 채우지 못하면 <b>필요한 개수를 알려 주고 멈춘다.</b> 거래 0 건짜리 결과를
     * 조용히 돌려주지 않는다.
     *
     * <p>둘은 다른 사실이다 — "신호가 없었다" 와 "판단할 근거조차 없었다". 후자를 전자처럼
     * 보여 주면 전략이 아무것도 하지 않은 것으로 읽힌다. 부르는 쪽의 대응도 다르다:
     * 캔들을 더 받아 오면 된다.
     */
    @Test
    void 워밍업에_못_미치는_시리즈는_필요한_개수를_알려_준다() {
        CandleSeries tooShort = new CandleSeries(SERIES.candles().subList(0, 40));

        assertThatThrownBy(() -> ENGINE.run(spec(), tooShort))
                .isInstanceOf(InsufficientCandlesException.class)
                .hasMessageContaining("40");
    }

    @Test
    void 거래가_한_건도_없으면_손익비를_말할_수_없다() {
        BacktestResult result = ENGINE.run(spec().withIndicatorFilter(true),
                BacktestFixtures.zigzag(12, 8, 59000, 59200));

        assertThat(result.totalTrades()).isZero();
        assertThat(result.profitFactor()).isEmpty();
        assertThat(result.finalEquity()).isEqualTo(Money.of("800"));
    }
}
