package com.coinwin.journal.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import java.time.Instant;

/**
 * 실제로 체결된 한 건. 계획의 {@code PlannedEntry} 와 짝을 이루지만 <b>다른 것</b>이다.
 *
 * <p>계획은 가격과 비중을 갖고, 체결은 가격과 수량과 시각을 갖는다. 비중이 아니라 수량인
 * 이유는 체결이 계획대로 되지 않기 때문이다 — 슬리피지로 가격이 밀리고, 부분 체결로 수량이
 * 모자란다. 계획과 체결을 한 타입으로 합치면 그 차이가 기록되지 않는다.
 */
public record Fill(Price price, Quantity quantity, Instant at) {

    public Fill {
        DomainValues.required(price, "체결가");
        DomainValues.required(quantity, "체결 수량");
        DomainValues.required(at, "체결 시각");
        if (quantity.value().signum() == 0) {
            throw new InvalidTradeException("체결 수량은 0 일 수 없다");
        }
    }

    /** 이 체결의 명목가. {@code 수량 × 체결가}. */
    public Money notional() {
        return quantity.times(price.asAmount());
    }
}
