package com.coinwin.journal.adapter.out.persistence;

import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Percentage;
import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import com.coinwin.journal.domain.ClosedTrade;
import com.coinwin.journal.domain.ExecutedEntries;
import com.coinwin.journal.domain.Exit;
import com.coinwin.journal.domain.Fill;
import com.coinwin.journal.domain.MarketContext;
import com.coinwin.journal.domain.OpenTrade;
import com.coinwin.journal.domain.PlannedTrade;
import com.coinwin.journal.domain.Trade;
import com.coinwin.journal.domain.TradeClosure;
import com.coinwin.journal.domain.TradeCosts;
import com.coinwin.journal.domain.TradeId;
import com.coinwin.position.domain.EntryLadder;
import com.coinwin.position.domain.PlannedEntry;
import com.coinwin.position.domain.PositionPlan;

/**
 * 엔티티 → 도메인. 쓰는 방향은 {@link TradeEntityWriter} 다.
 *
 * <p>읽으면서 <b>도메인 불변식을 전부 다시 통과한다.</b> 값 객체 생성자와 {@code Trade} 의
 * 생성 규칙이 그대로 적용되므로, 손으로 고친 행이나 깨진 마이그레이션이 조용히 도메인
 * 안으로 들어오지 못한다. 저장된 것을 무조건 믿지 않는 것이 이 방향의 값이다.
 *
 * <p>진입 시각·평단·손익 같은 파생값은 <b>저장하지도 읽지도 않는다.</b> 체결 내역에서 다시
 * 계산된다. 저장해 두면 어느 쪽이 맞는지 알 수 없는 두 번째 사본이 생긴다.
 */
final class TradeEntityReader {

    private TradeEntityReader() {
    }

    static Trade read(TradeEntity entity) {
        TradeId id = new TradeId(entity.id);
        PositionPlan plan = plan(entity);
        return switch (entity.state) {
            case PLANNED -> new PlannedTrade(id, plan, entity.plannedAt);
            case OPEN -> new OpenTrade(id, plan, entries(entity), context(entity),
                    entity.plannedAt);
            case CLOSED -> new ClosedTrade(id, plan, entries(entity), context(entity),
                    entity.plannedAt, closure(entity));
        };
    }

    static ClosedTrade readClosed(TradeEntity entity) {
        return (ClosedTrade) read(entity);
    }

    private static PositionPlan plan(TradeEntity entity) {
        EntryLadder ladder = new EntryLadder(entity.plannedEntries.stream()
                .map(row -> new PlannedEntry(
                        Price.of(row.price), Percentage.of(row.allocation)))
                .toList());
        return new PositionPlan(entity.direction, ladder,
                Price.of(entity.stopLoss), Price.of(entity.takeProfit), entity.leverage);
    }

    private static ExecutedEntries entries(TradeEntity entity) {
        return new ExecutedEntries(entity.fills.stream()
                .map(row -> new Fill(
                        Price.of(row.price), Quantity.of(row.quantity), row.filledAt))
                .toList());
    }

    private static MarketContext context(TradeEntity entity) {
        return new MarketContext(Price.of(entity.priceAtEntry),
                entity.ichimokuPosition, entity.bollingerPosition, entity.rationale);
    }

    private static TradeClosure closure(TradeEntity entity) {
        return new TradeClosure(
                new Exit(Price.of(entity.exitPrice), entity.exitAt),
                entity.exitReason,
                new TradeCosts(Money.of(entity.fees), Money.of(entity.funding)));
    }
}
