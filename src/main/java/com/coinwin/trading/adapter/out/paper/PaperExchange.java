package com.coinwin.trading.adapter.out.paper;

import com.coinwin.backtest.domain.CostModel;
import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Price;
import com.coinwin.position.domain.Direction;
import com.coinwin.trading.domain.OrderId;
import com.coinwin.trading.domain.OrderIntent;
import com.coinwin.trading.domain.OrderKind;
import com.coinwin.trading.domain.PaperLedger;
import com.coinwin.trading.domain.PlacedOrder;
import com.coinwin.trading.domain.TradingMode;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 거래소 흉내. 주문을 받고, 장부에 쌓고, <b>가격이 움직이면 걸려 있던 트리거를 체결한다.</b>
 *
 * <p>마지막 하나가 이 클래스의 존재 이유다. 손절이 걸리기만 하고 영영 체결되지 않으면 모의
 * 기록은 <b>지는 거래가 하나도 없는 거짓 장부</b>가 된다.
 *
 * <p><b>표시가 하나로 판정한다.</b> 봉의 고저를 쓰지 않는 이유는 봇이 그것을 보지 않기
 * 때문이다 — 사이클마다 한 값을 보고 그 값으로 체결된다. 실제보다 낙관적인 자리가 하나
 * 남는데(봉 안에서 스쳤다 돌아온 트리거를 놓친다), 그것은 <b>주기를 짧게 하면 줄고 길게 하면
 * 커지는</b> 성질이라 기록에 주기가 함께 남는 것으로 족하다.
 *
 * <p>포지션이 닫히면 남은 트리거를 전부 지운다 — 바이낸스의 {@code closePosition} 주문이
 * 그렇게 행동한다. 안 지우면 다음 진입이 <b>앞 거래의 손절을 물려받는다.</b>
 *
 * <p><b>추격 손절은 상태를 갖는다.</b> 어디서 터질지가 지나온 가격에 달렸으므로 극값을
 * 기억해야 한다 — 그 기억은 {@link TrailingWatch} 가 들고, 여기서는 주문이 사라질 때
 * 함께 지워지는 것만 지킨다. 안 지우면 다음 거래의 추격 손절이 <b>앞 거래의 최고점에서</b>
 * 따라오기 시작한다.
 */
final class PaperExchange {

    private final CostModel costs;
    private final Clock clock;
    private final AtomicLong sequence = new AtomicLong();
    private final Map<OrderId, PlacedOrder> resting = new ConcurrentHashMap<>();
    private final TrailingWatch trailing = new TrailingWatch();

    private PaperLedger ledger;

    PaperExchange(CostModel costs, Clock clock, Money startingEquity) {
        this.costs = DomainValues.required(costs, "비용 모델");
        this.clock = DomainValues.required(clock, "시계");
        this.ledger = PaperLedger.start(
                DomainValues.required(startingEquity, "시작 자산"), today());
    }

    synchronized PlacedOrder place(OrderIntent intent) {
        DomainValues.required(intent, "주문 의도");
        if (intent.kind().rests()) {
            PlacedOrder order = new PlacedOrder(
                    nextId(), intent, Optional.empty(), clock.instant(), TradingMode.PAPER);
            resting.put(order.id(), order);
            return order;
        }
        return fillNow(intent);
    }

    synchronized void cancel(OrderId id) {
        resting.remove(DomainValues.required(id, "주문 식별자"));
        trailing.forget(id);
    }

    synchronized List<PlacedOrder> restingOrders() {
        return List.copyOf(resting.values());
    }

    synchronized PaperLedger ledger() {
        return ledger;
    }

    /**
     * 표시가가 여기까지 왔다. 닿은 트리거를 체결한다.
     *
     * <p><b>손절을 익절보다 먼저 본다.</b> 한 걸음에 둘 다 닿았을 때 유리한 쪽을 고르면
     * 장부가 실제보다 좋게 나온다 — 백테스트 엔진이 같은 순서를 쓴다. 추격 손절도 손절이라
     * 같은 쪽에 선다.
     *
     * <p><b>극값을 먼저 갱신한다.</b> 이번 걸음이 새 최고점이면 추격 손절은 그 자리에서
     * 터지지 않는다 — 올라간 봉에서 손절이 체결되는 것은 방향이 뒤집힌 고장이다.
     */
    synchronized void advanceTo(Price mark) {
        DomainValues.required(mark, "표시가");
        trail(mark);
        touched(mark, OrderKind.TRAILING_STOP).forEach(order -> fillResting(order, mark));
        touched(mark, OrderKind.STOP_LOSS).forEach(order -> fillResting(order, mark));
        touched(mark, OrderKind.TAKE_PROFIT).forEach(order -> fillResting(order, mark));
    }

    private void trail(Price mark) {
        for (PlacedOrder order : resting.values()) {
            if (order.intent().kind() == OrderKind.TRAILING_STOP) {
                trailing.follow(order, mark);
            }
        }
    }

    private List<PlacedOrder> touched(Price mark, OrderKind kind) {
        List<PlacedOrder> hit = new ArrayList<>();
        for (PlacedOrder order : resting.values()) {
            if (order.intent().kind() == kind && reaches(order, mark)) {
                hit.add(order);
            }
        }
        return hit;
    }

    /**
     * 트리거에 닿았는가. 손절은 불리한 쪽에서, 익절은 유리한 쪽에서 닿는다 —
     * 롱의 손절은 아래로, 숏의 손절은 위로. 추격 손절은 걸린 값이 없으므로 따로 묻는다.
     */
    private boolean reaches(PlacedOrder order, Price mark) {
        if (order.intent().kind() == OrderKind.TRAILING_STOP) {
            return trailing.fires(order, mark);
        }
        Price trigger = order.intent().trigger().orElseThrow();
        boolean fallsTo = order.intent().position() == Direction.LONG
                == (order.intent().kind() == OrderKind.STOP_LOSS);
        return fallsTo ? !mark.isAbove(trigger) : !mark.isBelow(trigger);
    }

    private void fillResting(PlacedOrder order, Price mark) {
        resting.remove(order.id());
        trailing.forget(order.id());
        closeAt(order.intent(), slipped(order.intent(), mark));
    }

    private PlacedOrder fillNow(OrderIntent intent) {
        Price fill = slipped(intent, intent.reference());
        if (intent.kind() == OrderKind.ENTRY) {
            ledger = ledger.opened(intent.position(), intent.quantity().orElseThrow(), fill);
        } else {
            closeAt(intent, fill);
        }
        return new PlacedOrder(
                nextId(), intent, Optional.of(fill), clock.instant(), TradingMode.PAPER);
    }

    /**
     * 주문이 말한 만큼 닫는다. <b>전량이 닫힌 뒤에만</b> 남은 트리거를 지운다 — 절반만
     * 나갔는데 지우면 남은 절반이 보호 없이 열려 있게 된다. 전량이 닫혔는데 안 지우면
     * 다음 진입이 앞 거래의 손절을 물려받는다.
     */
    private void closeAt(OrderIntent intent, Price fill) {
        ledger = ledger.closed(fill, intent.quantity(), feesFor(intent, fill), today());
        if (ledger.position().isEmpty()) {
            resting.clear();
            trailing.clear();
        }
    }

    private Money feesFor(OrderIntent intent, Price fill) {
        return ledger.position()
                .map(open -> intent.quantity().orElse(open.quantity()))
                .map(quantity -> costs.exitFee(quantity.times(fill.asAmount())))
                .orElse(Money.of("0"));
    }

    /**
     * 미끄러짐은 언제나 불리한 쪽이다. <b>여는 쪽은 부호가 뒤집힌다</b> — 롱을 여는 것은
     * 사는 것이라 더 비싸게 체결되고, 그것은 숏을 닫는 것과 같은 방향이다.
     */
    private Price slipped(OrderIntent intent, Price at) {
        Direction pushAgainst = intent.kind().reducesPosition()
                ? intent.position()
                : opposite(intent.position());
        return costs.applyExitSlippage(at, pushAgainst);
    }

    private static Direction opposite(Direction direction) {
        return direction == Direction.LONG ? Direction.SHORT : Direction.LONG;
    }

    private java.time.LocalDate today() {
        return clock.instant().atZone(ZoneOffset.UTC).toLocalDate();
    }

    private OrderId nextId() {
        return new OrderId("paper-" + sequence.incrementAndGet());
    }
}
