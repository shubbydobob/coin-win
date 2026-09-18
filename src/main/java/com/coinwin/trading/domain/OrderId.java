package com.coinwin.trading.domain;

/**
 * 낸 주문을 가리키는 값.
 *
 * <p><b>거래소가 정한 것을 그대로 쓴다.</b> 우리가 만든 식별자를 쓰면 취소할 때 다시
 * 맞춰야 하고, 그 대응표가 틀리면 <b>지우려던 것과 다른 주문이 지워진다.</b>
 *
 * <p>장부 모드는 장부가 정한다 — 그쪽에서는 이 값이 거래소가 아니라 장부의 줄 번호다.
 */
public record OrderId(String value) {

    public OrderId {
        if (value == null || value.isBlank()) {
            throw new InvalidOrderException("주문 식별자가 비어 있다");
        }
    }
}
