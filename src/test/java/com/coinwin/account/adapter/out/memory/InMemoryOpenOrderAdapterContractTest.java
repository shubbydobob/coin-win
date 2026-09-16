package com.coinwin.account.adapter.out.memory;

import com.coinwin.account.application.port.out.LoadOpenOrdersPort;
import com.coinwin.account.application.port.out.OpenOrdersPortContract;
import com.coinwin.account.domain.ProtectiveOrder;
import com.coinwin.account.domain.ProtectiveOrderKind;
import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import com.coinwin.market.domain.Symbol;
import com.coinwin.position.domain.Direction;

/**
 * 인메모리 어댑터가 <b>바이낸스 어댑터와 같은 계약</b>을 지키는가.
 *
 * <p>다른 종목 주문을 하나 섞어 둔다. 바이낸스는 거래소가 서버에서 거르지만 이쪽은 우리가
 * 걸러야 하고, <b>두 구현이 같은 장면에 같은 답을 내는지</b>가 이 스위트의 전부다.
 */
class InMemoryOpenOrderAdapterContractTest extends OpenOrdersPortContract {

    @Override
    protected LoadOpenOrdersPort port() {
        return new InMemoryOpenOrderAdapter(
                ProtectiveOrder.entirePosition(Symbol.BTC_USDT, Direction.LONG,
                        ProtectiveOrderKind.STOP_LOSS, Price.of("58000")),
                ProtectiveOrder.partial(Symbol.BTC_USDT, Direction.LONG,
                        ProtectiveOrderKind.TAKE_PROFIT,
                        new ProtectiveOrder.PartialSize(Price.of("64000"), Quantity.of("0.05"))),
                ProtectiveOrder.entirePosition(Symbol.BTC_USDT, Direction.SHORT,
                        ProtectiveOrderKind.TRAILING_STOP, null),
                ProtectiveOrder.entirePosition(Symbol.of("ETHUSDT"), Direction.LONG,
                        ProtectiveOrderKind.STOP_LOSS, Price.of("3000")));
    }
}
