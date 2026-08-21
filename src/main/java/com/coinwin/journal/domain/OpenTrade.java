package com.coinwin.journal.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import com.coinwin.position.domain.PositionPlan;
import java.time.Instant;

/**
 * 체결됐고 아직 닫히지 않은 포지션.
 *
 * <p><b>손익을 물어볼 수 없다.</b> 미실현 손익은 현재가에 따라 매 순간 달라지므로 기록의
 * 대상이 아니고, 그것을 알고 싶으면 거래소 화면을 보면 된다. 이 모듈이 답하는 것은 끝난
 * 거래에 대한 질문이다.
 */
public record OpenTrade(
        TradeId id,
        PositionPlan plan,
        ExecutedEntries entries,
        MarketContext context,
        Instant plannedAt) implements Trade {

    public OpenTrade {
        DomainValues.required(id, "거래 식별자");
        DomainValues.required(plan, "매매 계획");
        DomainValues.required(entries, "진입 체결 내역");
        DomainValues.required(context, "진입 시점 시장 상태");
        DomainValues.required(plannedAt, "계획 시각");
    }

    /** 포지션이 열린 시각. 첫 체결이다 — 별도 필드로 두면 체결 내역과 어긋날 수 있다. */
    public Instant openedAt() {
        return entries.firstFilledAt();
    }

    /** 실제 평단. 계획한 평단과 다를 수 있고, 그 차이가 슬리피지다. */
    public Price averageEntryPrice() {
        return entries.averagePrice();
    }

    public Quantity quantity() {
        return entries.totalQuantity();
    }

    /**
     * 계획한 모든 분할이 체결됐는가.
     *
     * <p>1차만 체결된 채로 닫힌 거래는 계획대로 흘러간 것이 아니다. 집계에서 손익만 보면
     * 그 사실이 사라지므로 상태로 남긴다.
     */
    public boolean fullyFilled() {
        return entries.count() >= plan.entries().size();
    }

    /**
     * 포지션을 닫는다.
     *
     * @throws InvalidTradeException 청산이 마지막 진입 체결보다 앞선 경우
     */
    public ClosedTrade close(TradeClosure closure) {
        DomainValues.required(closure, "청산 정보");
        Instant closedAt = closure.exit().at();
        if (closedAt.isBefore(entries.lastFilledAt())) {
            throw new InvalidTradeException(
                    "청산은 마지막 진입 체결보다 앞설 수 없다: 마지막 체결 %s, 청산 %s"
                            .formatted(entries.lastFilledAt(), closedAt));
        }
        return new ClosedTrade(id, plan, entries, context, plannedAt, closure);
    }
}
