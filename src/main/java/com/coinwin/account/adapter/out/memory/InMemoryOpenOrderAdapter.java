package com.coinwin.account.adapter.out.memory;

import com.coinwin.account.application.port.out.LoadOpenOrdersPort;
import com.coinwin.account.domain.ProtectiveOrder;
import com.coinwin.market.domain.Symbol;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 키 없이 도는 미체결 주문 어댑터.
 *
 * <p>{@code InMemoryExchangePositionAdapter} 와 같은 자리다 — 서비스 테스트를 키 없이 돌리기
 * 위해 있고, 운영 컨텍스트에는 빈으로 올라오지 않는다. 폴백으로 두면 키가 없을 때
 * <b>"손절이 걸려 있지 않다" 는 거짓 경고</b>가 뜨거나 그 반대가 된다.
 */
public class InMemoryOpenOrderAdapter implements LoadOpenOrdersPort {

    private final List<ProtectiveOrder> orders = new CopyOnWriteArrayList<>();

    public InMemoryOpenOrderAdapter(ProtectiveOrder... seed) {
        this.orders.addAll(List.of(seed));
    }

    @Override
    public List<ProtectiveOrder> protectiveOrdersFor(Symbol symbol) {
        return orders.stream().filter(order -> order.symbol().equals(symbol)).toList();
    }

    /** 테스트가 도중에 거래소 상태를 바꾸고 싶을 때. */
    public void replaceWith(Collection<ProtectiveOrder> replacement) {
        List<ProtectiveOrder> copy = new ArrayList<>(replacement);
        orders.clear();
        orders.addAll(copy);
    }
}
