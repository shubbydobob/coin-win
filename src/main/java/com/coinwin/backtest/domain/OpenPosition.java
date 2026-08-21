package com.coinwin.backtest.domain;

import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import com.coinwin.journal.domain.ClosedTrade;
import com.coinwin.journal.domain.ExecutedEntries;
import com.coinwin.journal.domain.Exit;
import com.coinwin.journal.domain.ExitReason;
import com.coinwin.journal.domain.Fill;
import com.coinwin.journal.domain.TradeClosure;
import com.coinwin.journal.domain.TradeCosts;
import com.coinwin.journal.domain.TradeId;
import com.coinwin.market.domain.Candle;
import com.coinwin.position.domain.Direction;
import com.coinwin.position.domain.PositionPlan;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 열려 있는 포지션. 체결된 회차와 지금까지 낸 수수료를 들고 있다.
 *
 * <p><b>1차만 체결된 채로도 살아 있다.</b> 2차 지정가는 포지션이 닫힐 때까지 유효하고,
 * 손절·익절은 그 시점의 체결 수량 기준으로 적용된다. Phase 1 이 {@code FillState} 를 분할 체결
 * 단계별로 들고 있는 이유가 이것이다.
 *
 * @param order 무장 당시의 계획과 총수량
 * @param fills 지금까지의 진입 체결
 * @param fees 지금까지 낸 진입 수수료 누계
 */
record OpenPosition(ArmedOrder order, List<Fill> fills, Money fees) {

    OpenPosition {
        fills = List.copyOf(fills);
    }

    static OpenPosition opened(ArmedOrder order, Price price, Instant at, CostModel costs) {
        return new OpenPosition(order, List.of(), Money.of("0")).withFill(price, at, costs, 0);
    }

    Direction direction() {
        return order.direction();
    }

    PositionPlan plan() {
        return order.signal().plan();
    }

    boolean fullyFilled() {
        return fills.size() >= plan().entries().size();
    }

    /** 아직 안 채워진 다음 회차의 지정가. 전량 체결됐으면 비어 있다. */
    Optional<Price> nextEntryFill(Candle candle) {
        if (fullyFilled()) {
            return Optional.empty();
        }
        Price limit = plan().entries().entries().get(fills.size()).price();
        return Trigger.adverse(direction(), limit).fillIn(candle);
    }

    OpenPosition withFill(Price price, Instant at, CostModel costs, int slice) {
        Quantity quantity = order.sliceQuantity(slice);
        Fill fill = new Fill(price, quantity, at);
        List<Fill> extended = new ArrayList<>(fills);
        extended.add(fill);
        return new OpenPosition(order, extended, fees.plus(costs.entryFee(fill.notional())));
    }

    OpenPosition withNextFill(Price price, Instant at, CostModel costs) {
        return withFill(price, at, costs, fills.size());
    }

    /**
     * 이 봉에서 닫혔는가. <b>손절이 익절보다 먼저다.</b>
     *
     * <p>OHLC 로는 봉 내부 경로를 알 수 없다. 둘 다 닿았을 때 유리한 쪽을 고르면 백테스트가
     * 실제보다 좋게 나오고, 이 도구에서 그 방향의 오차는 허용되지 않는다. "진입가에서 더 가까운
     * 쪽이 먼저 닿았을 것" 같은 추정은 그럴듯하지만 근거가 없고 갭으로 시작한 봉에서는 확실히
     * 틀린 답을 낸다.
     *
     * <p>체결가에는 슬리피지가 붙는다 — 손절·익절 모두 트리거 체결이다.
     */
    Optional<ClosedTrade> closeIn(Candle candle, CostModel costs) {
        Optional<ClosedTrade> stopped = closeOn(candle, costs,
                Trigger.adverse(direction(), plan().stopLoss()), ExitReason.PLANNED_STOP);
        if (stopped.isPresent()) {
            return stopped;
        }
        return closeOn(candle, costs,
                Trigger.benign(direction(), plan().takeProfit()), ExitReason.PLANNED_TARGET);
    }

    private Optional<ClosedTrade> closeOn(
            Candle candle, CostModel costs, Trigger trigger, ExitReason reason) {
        return trigger.fillIn(candle).map(price -> closeAt(
                costs.applyExitSlippage(price, direction()), candle.openTime(), reason, costs));
    }

    /**
     * 청산. 수수료는 진입 누계에 청산분을 더한 것이다.
     *
     * <p>펀딩비는 0 이다 — 캔들만으로는 재현할 수 없다. {@code TradeCosts} 가 그것을 별도
     * 필드로 들고 있으므로 <b>모델에 없다는 사실이 결과에 드러난다.</b> 수수료에 섞어 넣으면
     * 그 한계가 보이지 않게 된다.
     */
    ClosedTrade closeAt(Price exitPrice, Instant at, ExitReason reason, CostModel costs) {
        ExecutedEntries entries = new ExecutedEntries(fills);
        Money exitNotional = entries.totalQuantity().times(exitPrice.asAmount());
        TradeCosts total = new TradeCosts(fees.plus(costs.exitFee(exitNotional)), Money.of("0"));
        return new ClosedTrade(identifier(entries.firstFilledAt()), plan(), entries,
                order.signal().context(), order.plannedAt(),
                new TradeClosure(new Exit(exitPrice, at), reason, total));
    }

    /**
     * 진입 시각에서 유도한 식별자.
     *
     * <p>{@link TradeId#random()} 을 쓰면 같은 스펙을 두 번 돌린 결과가 서로 달라져 완료 조건이
     * 그 자리에서 무너진다. 동시 포지션이 1개이므로 진입 시각은 거래마다 유일하다.
     */
    private static TradeId identifier(Instant openedAt) {
        return new TradeId(UUID.nameUUIDFromBytes(
                ("coinwin:backtest:" + openedAt).getBytes(StandardCharsets.UTF_8)));
    }
}
