package com.coinwin.trading.adapter.out.paper;

import com.coinwin.common.domain.Price;
import com.coinwin.position.domain.Direction;
import com.coinwin.trading.domain.OrderId;
import com.coinwin.trading.domain.PlacedOrder;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 추격 손절이 따라온 자리를 기억한다. <b>극값 하나면 된다.</b>
 *
 * <p>추격 손절은 걸려 있는 값이 아니라 <b>지나온 가격의 함수</b>라 다른 트리거 주문과 달리
 * 상태를 갖는다. 그 상태가 {@code PaperExchange} 안에 섞이면 그 클래스가 "주문을 받는다" 와
 * "가격의 역사를 센다" 둘을 하게 된다.
 *
 * <p><b>극값은 가장 유리했던 자리다</b> — 롱이면 최고가, 숏이면 최저가. 불리한 쪽으로는
 * 절대 움직이지 않는다. 움직이면 그것은 추격이 아니라 손절이 따라 내려가는 것이고,
 * {@code ExitRuleStrategy} 가 고정 손절에서 피한 바로 그 고장이다.
 *
 * <p>터지는 가격을 여기서 계산하지 않고 {@code CallbackRate} 에게 묻는다 — 장부와 거래소가
 * 다른 자로 재면 두 기록을 나란히 놓을 수 없다.
 */
final class TrailingWatch {

    private final Map<OrderId, Price> extremes = new ConcurrentHashMap<>();

    /** 이 주문이 본 가장 유리한 자리를 갱신한다. 처음 보는 주문이면 지금 값으로 시작한다. */
    void follow(PlacedOrder order, Price mark) {
        extremes.merge(order.id(), mark,
                (seen, now) -> better(order.intent().position(), seen, now));
    }

    /** 되돌아온 폭이 추격 폭에 닿았는가. 극값을 아직 못 본 주문은 터지지 않는다. */
    boolean fires(PlacedOrder order, Price mark) {
        Price extreme = extremes.get(order.id());
        if (extreme == null) {
            return false;
        }
        Direction position = order.intent().position();
        Price stop = order.intent().callbackRate().orElseThrow().stopFrom(extreme, position);
        return position == Direction.LONG ? !mark.isAbove(stop) : !mark.isBelow(stop);
    }

    void forget(OrderId id) {
        extremes.remove(id);
    }

    void clear() {
        extremes.clear();
    }

    private static Price better(Direction position, Price seen, Price now) {
        boolean improves = position == Direction.LONG ? now.isAbove(seen) : now.isBelow(seen);
        return improves ? now : seen;
    }
}
