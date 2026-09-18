package com.coinwin.trading.domain;

import com.coinwin.common.domain.DomainValues;
import java.util.List;

/**
 * 전략이 이번 사이클에 하고 싶은 것 전부 — <b>낼 주문과 지울 주문.</b>
 *
 * <p><b>지우는 쪽이 늘어난 이유는 R4 다.</b> 손절을 본전으로 옮기려면 옛 손절을 없애야 하는데,
 * 전략이 낼 줄만 알면 그것을 할 수 없다 — 손절이 둘 걸린 채로 남거나, 옮기는 일을 사람이
 * 해야 한다. 규칙이 손절을 관리하려면 없애는 것도 규칙의 일이다.
 *
 * <p><b>내는 것과 지우는 것을 한 타입에 담되 섞지 않는다.</b> 순서가 중요하기 때문이다 —
 * 서비스는 <b>먼저 내고 나중에 지운다.</b> 반대로 하면 새 손절이 실패했을 때 <b>보호가 없는
 * 순간</b>이 생긴다. 잠깐 손절이 둘인 것은 안전하다(둘 다 전량 청산이라 하나가 터지면 나머지는
 * 거래소가 지운다).
 *
 * @param place 낼 주문. 안전장치를 통과한 것만 실제로 나간다
 * @param cancel 지울 주문의 식별자
 */
public record CycleDecision(List<OrderIntent> place, List<OrderId> cancel) {

    public CycleDecision {
        DomainValues.required(place, "낼 주문");
        DomainValues.required(cancel, "지울 주문");
        place = List.copyOf(place);
        cancel = List.copyOf(cancel);
    }

    /** 아무것도 하지 않는다. <b>대부분의 사이클이 이렇다.</b> */
    public static CycleDecision none() {
        return new CycleDecision(List.of(), List.of());
    }

    /** 내기만 한다. */
    public static CycleDecision place(OrderIntent... intents) {
        return new CycleDecision(List.of(intents), List.of());
    }

    public boolean isEmpty() {
        return place.isEmpty() && cancel.isEmpty();
    }
}
