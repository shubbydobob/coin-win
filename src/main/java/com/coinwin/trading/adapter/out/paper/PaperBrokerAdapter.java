package com.coinwin.trading.adapter.out.paper;

import com.coinwin.backtest.domain.CostModel;
import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Price;
import com.coinwin.position.domain.Direction;
import com.coinwin.trading.application.port.out.PlaceOrderPort;
import com.coinwin.trading.domain.OrderId;
import com.coinwin.trading.domain.OrderIntent;
import com.coinwin.trading.domain.PlacedOrder;
import com.coinwin.trading.domain.TradingMode;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 거래소 대신 장부에 적는 브로커. <b>봇의 기본 모드다.</b>
 *
 * <p>같은 루프·같은 판단·같은 {@link OrderIntent} 가 여기까지 오고, 마지막 한 칸에서만
 * 갈린다. 그래서 장부에서 도는 것을 확인하면 <b>실계좌에서 달라지는 것은 체결뿐</b>이다.
 *
 * <p><b>백테스트와 같은 {@link CostModel} 을 쓴다.</b> 모의 기록과 백테스트 결과를 나란히
 * 놓는 것이 이 봇의 검증 방법이므로({@code trading-bot.md} § 5의 C) 두 곳이 다른 자로 재면
 * 그 대조가 무의미해진다. 수수료·슬리피지 규칙이 갈라지지 않도록 객체를 그대로 가져온다.
 *
 * <p><b>지정가를 다루지 않는다.</b> 봉 안에서 지정가가 닿았는지는 OHLC 로 알 수 없고,
 * 추측해서 체결하면 장부가 실제보다 좋게 나온다 — 이 도구에서 허용되지 않는 방향의 오차다.
 * 봇이 내는 주문은 시장가와 트리거뿐이라 모자라지 않는다({@code OrderKind}).
 */
public class PaperBrokerAdapter implements PlaceOrderPort {

    private final CostModel costs;
    private final Clock clock;
    private final AtomicLong sequence = new AtomicLong();
    private final Map<OrderId, PlacedOrder> resting = new ConcurrentHashMap<>();

    public PaperBrokerAdapter(CostModel costs, Clock clock) {
        this.costs = DomainValues.required(costs, "비용 모델");
        this.clock = DomainValues.required(clock, "시계");
    }

    @Override
    public TradingMode mode() {
        return TradingMode.PAPER;
    }

    @Override
    public PlacedOrder place(OrderIntent intent) {
        DomainValues.required(intent, "주문 의도");
        PlacedOrder placed = new PlacedOrder(nextId(), intent, fillFor(intent),
                clock.instant(), TradingMode.PAPER);
        if (placed.resting()) {
            resting.put(placed.id(), placed);
        }
        return placed;
    }

    /** 없는 주문을 지우는 것은 실패가 아니다 — 이미 없다는 원하던 상태다. */
    @Override
    public void cancel(OrderId id) {
        DomainValues.required(id, "주문 식별자");
        resting.remove(id);
    }

    /** 장부에 걸려 있는 트리거 주문. 포트에는 없다 — 검사와 화면을 위한 자리다. */
    public List<PlacedOrder> restingOrders() {
        return List.copyOf(resting.values());
    }

    /**
     * 체결가. 트리거 주문은 걸려만 있으므로 비어 있다.
     *
     * <p><b>슬리피지는 언제나 불리한 쪽이다.</b> 여는 쪽은 부호가 뒤집힌다 — 롱을 여는 것은
     * <i>사는 것</i>이라 더 비싸게 체결되고, 그것은 <i>숏을 닫는 것</i>과 같은 방향이다.
     * 부호를 잘못 잡으면 장부가 실제보다 좋게 나온다.
     */
    private Optional<Price> fillFor(OrderIntent intent) {
        if (intent.kind().needsTrigger()) {
            return Optional.empty();
        }
        Direction pushAgainst = intent.kind().reducesPosition()
                ? intent.position()
                : opposite(intent.position());
        return Optional.of(costs.applyExitSlippage(intent.reference(), pushAgainst));
    }

    private static Direction opposite(Direction direction) {
        return direction == Direction.LONG ? Direction.SHORT : Direction.LONG;
    }

    private OrderId nextId() {
        return new OrderId("paper-" + sequence.incrementAndGet());
    }
}
