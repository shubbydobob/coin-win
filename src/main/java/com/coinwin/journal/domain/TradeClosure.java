package com.coinwin.journal.domain;

import com.coinwin.common.domain.DomainValues;

/**
 * 포지션을 닫으며 확정된 것 세 가지 — 언제 얼마에, 왜, 비용은 얼마나.
 *
 * <p>{@link ClosedTrade} 에 셋을 평평하게 늘어놓지 않고 묶은 이유는 <b>함께 정해지고 함께
 * 쓰이기</b> 때문이다. 청산 없이 이유만 있을 수 없고, 이유 없이 비용만 있을 수 없다.
 * {@link OpenTrade#close} 가 인자 하나만 받는 것도 그 덕이다.
 */
public record TradeClosure(Exit exit, ExitReason reason, TradeCosts costs) {

    public TradeClosure {
        DomainValues.required(exit, "청산 체결");
        DomainValues.required(reason, "청산 이유");
        DomainValues.required(costs, "거래 비용");
    }

    /** 계획대로 닫혔는가. 판정은 {@link ExitReason} 이 소유한다. */
    public boolean honorsPlan() {
        return reason.honorsPlan();
    }
}
