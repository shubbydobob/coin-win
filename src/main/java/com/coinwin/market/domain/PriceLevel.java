package com.coinwin.market.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.InvalidValueException;
import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;

/**
 * 호가 한 단 — 이 가격에 이만큼 걸려 있다.
 *
 * <p>잔량 0 을 허용하지 않는다. 거래소는 잔량이 0 이 된 단을 목록에서 빼고 주므로 0 이 오는
 * 것은 응답을 잘못 읽었다는 뜻이고, 그것을 담아 두면 잔량 합과 불균형이 조용히 틀린다.
 */
public record PriceLevel(Price price, Quantity quantity) {

    public PriceLevel {
        DomainValues.required(price, "호가");
        DomainValues.required(quantity, "잔량");
        if (quantity.value().signum() <= 0) {
            throw new InvalidValueException("잔량은 0보다 커야 한다: " + quantity.value());
        }
    }
}
