package com.coinwin.trading.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import com.coinwin.position.domain.Direction;

/**
 * 봇이 보는 열린 포지션. <b>{@code account} 의 {@code ExchangePosition} 이 아니다.</b>
 *
 * <p>전략이 판단하는 데 필요한 것은 셋뿐이다 — 어느 쪽으로 · 얼마나 · 어디서 들어갔나.
 * 거래소가 주는 나머지(청산가 · 미실현 · 증거금)는 화면이 읽는 사실이지 전략의 입력이
 * 아니고, 그것까지 끌어오면 {@code trading} 이 {@code account} 의 타입에 묶인다.
 *
 * <p>좁게 두는 값이 하나 더 있다 — <b>이 타입에는 관측 시각이 없다.</b> 언제 본 것인가는
 * {@link MarketView} 가 한 번만 갖는다. 두 곳에 있으면 둘이 어긋날 수 있다.
 */
public record BotPosition(Direction direction, Quantity quantity, Price entry) {

    public BotPosition {
        DomainValues.required(direction, "방향");
        DomainValues.required(quantity, "수량");
        DomainValues.required(entry, "평단");
        if (quantity.value().signum() <= 0) {
            throw new InvalidOrderException("열린 포지션의 수량은 0 보다 커야 한다");
        }
    }
}
