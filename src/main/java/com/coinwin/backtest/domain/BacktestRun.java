package com.coinwin.backtest.domain;

import com.coinwin.common.domain.Money;
import com.coinwin.journal.domain.ClosedTrade;
import com.coinwin.market.domain.Candle;
import com.coinwin.market.domain.CandleSeries;
import com.coinwin.position.domain.MaintenanceMarginPolicy;
import com.coinwin.position.domain.RiskBudget;
import com.coinwin.position.domain.RiskExceedsBalanceException;
import com.coinwin.position.domain.StopBeyondLiquidationException;
import com.coinwin.projection.domain.EquityCurve;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 한 번의 백테스트 실행. 봉을 하나씩 밟으며 상태를 옮긴다.
 *
 * <p>봉 하나의 처리 순서가 이 클래스의 전부다.
 *
 * <ol>
 *   <li>포지션이 없으면 <b>직전 봉에서 무장한</b> 1차 지정가를 이 봉에 대 본다
 *   <li>포지션이 있으면 2차 체결 → 손절 → 익절 순으로 본다
 *   <li>봉이 닫히면, 포지션이 없을 때에 한해 이 봉의 종가로 다음 봉을 무장한다
 * </ol>
 *
 * <p>1번과 2번이 <b>같은 봉에서 이어진다.</b> 진입한 봉에서 바로 손절될 수 있다는 뜻이고,
 * 그것이 § 6 의 "보유자에게 불리한 순서" 원칙이다. 봉 내부 경로는 OHLC 로 알 수 없으므로
 * 유리한 쪽을 고르지 않는다.
 */
final class BacktestRun {

    private final BacktestSpec spec;
    private final MaintenanceMarginPolicy policy;
    private final ZoneReversalStrategy strategy;
    private final MarketTimeline timeline;

    private final List<ClosedTrade> trades = new ArrayList<>();
    private final List<Money> equityPoints = new ArrayList<>();
    private Money equity;
    private Optional<ArmedOrder> armed = Optional.empty();
    private Optional<OpenPosition> open = Optional.empty();

    BacktestRun(BacktestSpec spec, MaintenanceMarginPolicy policy, CandleSeries series) {
        this.spec = spec;
        this.policy = policy;
        this.strategy = new ZoneReversalStrategy(spec.strategy().rules());
        this.timeline = MarketTimeline.over(series, spec.strategy().zones());
        this.equity = spec.account().initialCapital();
        this.equityPoints.add(equity);
    }

    BacktestResult execute() {
        for (int index = timeline.firstTradableIndex(); index < timeline.size(); index++) {
            step(index);
        }
        return new BacktestResult(spec, List.copyOf(trades), new EquityCurve(equityPoints));
    }

    private void step(int index) {
        Candle candle = timeline.candleAt(index);
        if (open.isEmpty()) {
            tryEntry(candle);
        }
        open.ifPresent(position -> manage(position, candle));
        armed = open.isEmpty() ? armFrom(index) : Optional.empty();
    }

    private void tryEntry(Candle candle) {
        armed.ifPresent(order -> Trigger.adverse(order.direction(), order.firstEntryPrice())
                .fillIn(candle)
                .ifPresent(price -> {
                    open = Optional.of(order.openAt(price, candle.openTime(), spec.costs()));
                    armed = Optional.empty();
                }));
    }

    private void manage(OpenPosition position, Candle candle) {
        OpenPosition current = position.nextEntryFill(candle)
                .map(price -> position.withNextFill(price, candle.openTime(), spec.costs()))
                .orElse(position);
        Optional<ClosedTrade> closed = current.closeIn(candle, spec.costs());
        open = closed.isEmpty() ? Optional.of(current) : Optional.empty();
        closed.ifPresent(this::record);
    }

    private void record(ClosedTrade trade) {
        trades.add(trade);
        equity = equity.plus(trade.realizedPnl());
        equityPoints.add(equity);
    }

    /**
     * 이 봉의 종가로 다음 봉을 무장한다. 게이트 6·7 — 계좌가 감당할 수 있는 계획인가 — 이
     * 여기서 걸린다.
     *
     * <p>예외를 흘리지 않고 신호를 버린다. 열 수 없는 계획은 오류가 아니라 신호가 아닌 것이다.
     */
    private Optional<ArmedOrder> armFrom(int index) {
        if (!spec.account().canTradeWith(equity)) {
            return Optional.empty();
        }
        return strategy.signalAt(timeline.snapshotAt(index), timeline.readingAt(index),
                spec.account().leverage()).flatMap(signal -> arm(signal, index));
    }

    private Optional<ArmedOrder> arm(TradeSignal signal, int index) {
        try {
            RiskBudget budget = spec.account().budgetFor(equity);
            if (signal.plan().analyze(budget, policy).marginExceedsBalance()) {
                return Optional.empty();
            }
            return Optional.of(new ArmedOrder(signal, signal.plan().totalQuantity(budget),
                    timeline.candleAt(index).openTime()));
        } catch (StopBeyondLiquidationException | RiskExceedsBalanceException rejected) {
            return Optional.empty();
        }
    }
}
