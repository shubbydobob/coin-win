package com.coinwin.journal.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.position.domain.PositionPlan;
import java.time.Instant;

/**
 * 세워 두었으나 아직 한 건도 체결되지 않은 계획.
 *
 * <p>이 상태를 저장하는 것이 이 모듈의 첫 값이다. 계획을 진입 <b>전에</b> 남겨야 나중에
 * "그때 정말 그렇게 계획했었나" 를 되물을 수 있다. 진입 후에 적으면 결과를 아는 채로 쓰게 되고,
 * 그 기록으로는 계획 준수 여부를 판정할 수 없다.
 */
public record PlannedTrade(TradeId id, PositionPlan plan, Instant plannedAt) implements Trade {

    public PlannedTrade {
        DomainValues.required(id, "거래 식별자");
        DomainValues.required(plan, "매매 계획");
        DomainValues.required(plannedAt, "계획 시각");
    }

    public static PlannedTrade of(PositionPlan plan, Instant plannedAt) {
        return new PlannedTrade(TradeId.random(), plan, plannedAt);
    }

    /**
     * 체결됐다. 진입 시점의 시장 상태를 함께 받는다 — 나중에는 재현할 수 없는 값이다.
     *
     * @throws InvalidTradeException 첫 체결이 계획 시각보다 앞선 경우
     */
    public OpenTrade fill(ExecutedEntries entries, MarketContext context) {
        DomainValues.required(entries, "진입 체결 내역");
        if (entries.firstFilledAt().isBefore(plannedAt)) {
            throw new InvalidTradeException(
                    "체결은 계획보다 앞설 수 없다: 계획 %s, 첫 체결 %s"
                            .formatted(plannedAt, entries.firstFilledAt()));
        }
        return new OpenTrade(id, plan, entries, context, plannedAt);
    }
}
