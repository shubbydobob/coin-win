package com.coinwin.journal.adapter.out.persistence;

import com.coinwin.journal.domain.ClosedTrade;
import com.coinwin.journal.domain.ExecutedEntries;
import com.coinwin.journal.domain.MarketContext;
import com.coinwin.journal.domain.OpenTrade;
import com.coinwin.journal.domain.PlannedTrade;
import com.coinwin.journal.domain.Trade;
import com.coinwin.journal.domain.TradeClosure;
import com.coinwin.position.domain.PositionPlan;

/**
 * 도메인 → 엔티티. 읽는 방향은 {@link TradeEntityReader} 다.
 *
 * <p>두 방향을 한 클래스에 두면 200줄을 넘는다. 나눈 김에 얻은 것이 하나 있다 — 쓰기는
 * 기존 엔티티를 <b>덮어쓸 수 있어야</b> 하고(같은 거래의 다음 상태), 읽기는 새 객체를 만든다.
 * 두 책임이 실제로 다르다.
 */
final class TradeEntityWriter {

    private TradeEntityWriter() {
    }

    /** {@code target} 이 null 이면 새로 만든다. 아니면 그 행을 이 거래의 현재 상태로 덮어쓴다. */
    static TradeEntity write(Trade trade, TradeEntity target) {
        TradeEntity entity = target == null ? new TradeEntity() : target;
        entity.id = trade.id().value();
        entity.plannedAt = trade.plannedAt();
        writePlan(entity, trade.plan());
        clearOptionalColumns(entity);
        switch (trade) {
            case PlannedTrade ignored -> entity.state = TradeState.PLANNED;
            case OpenTrade open ->
                    writeOpen(entity, TradeState.OPEN, open.entries(), open.context());
            case ClosedTrade closed -> writeClosed(entity, closed);
        }
        return entity;
    }

    private static void writeClosed(TradeEntity entity, ClosedTrade closed) {
        writeOpen(entity, TradeState.CLOSED, closed.entries(), closed.context());
        writeClosure(entity, closed.closure());
    }

    private static void writeOpen(
            TradeEntity entity, TradeState state, ExecutedEntries entries, MarketContext context) {
        entity.state = state;
        entries.fills().forEach(fill -> entity.fills.add(new FillRow(
                fill.price().value(), fill.quantity().value(), fill.at())));
        entity.priceAtEntry = context.priceAtEntry().value();
        entity.ichimokuPosition = context.ichimokuPosition();
        entity.bollingerPosition = context.bollingerPosition();
        entity.rationale = context.rationale();
    }

    private static void writeClosure(TradeEntity entity, TradeClosure closure) {
        entity.exitPrice = closure.exit().price().value();
        entity.exitAt = closure.exit().at();
        entity.exitReason = closure.reason();
        entity.fees = closure.costs().fees().value();
        entity.funding = closure.costs().funding().value();
    }

    private static void writePlan(TradeEntity entity, PositionPlan plan) {
        entity.direction = plan.direction();
        entity.leverage = plan.leverage();
        entity.stopLoss = plan.stopLoss().value();
        entity.takeProfit = plan.takeProfit().value();
        entity.plannedEntries.clear();
        plan.entries().entries().forEach(entry -> entity.plannedEntries.add(
                new PlannedEntryRow(entry.price().value(), entry.allocation().value())));
    }

    /**
     * 상태에 맞지 않는 칸을 비운다.
     *
     * <p>덮어쓰기에서만 의미가 있는 것 같지만 그렇지 않다 — 이것이 없으면 CHECK 제약이
     * 잡아 주기 전까지 "청산됐다가 되돌아온" 행이 만들어질 여지가 남는다. 상태를 정하기
     * <b>전에</b> 한 번 비우고 필요한 것만 다시 채우는 편이 빠뜨릴 수 없다.
     */
    private static void clearOptionalColumns(TradeEntity entity) {
        entity.fills.clear();
        entity.priceAtEntry = null;
        entity.ichimokuPosition = null;
        entity.bollingerPosition = null;
        entity.rationale = null;
        entity.exitPrice = null;
        entity.exitAt = null;
        entity.exitReason = null;
        entity.fees = null;
        entity.funding = null;
    }
}
