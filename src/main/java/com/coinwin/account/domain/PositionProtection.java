package com.coinwin.account.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 포지션 하나가 손절로 덮여 있는가.
 *
 * <p>규칙 R1 — <b>손절 없는 포지션은 존재할 수 없다</b>({@code docs/spec/exit-automation.md}).
 * 이 타입은 그 규칙을 <b>강제하지 않고 판정만</b> 한다. 1단계는 읽기 전용 키로 도는 경고이고,
 * 주문을 거는 것은 3단계다 — 그때 {@code scope.md} 를 고친다.
 *
 * <p><b>있어야 할 손절가를 지어내지 않는다.</b> 기록에 계획이 있으면 그 손절가를 그대로
 * 말하고, 없으면 비워 둔다. 손절 거리의 기본값은 이 저장소가 아직 재지 않았고
 * ({@code exit-automation.md} § 4), 재지 않은 수를 화면에 놓으면 사람은 그것을 기준으로 읽는다.
 *
 * @param position 거래소가 말하는 지금 이 순간의 포지션
 * @param orders 이 포지션을 닫는 미체결 주문. 다른 방향·종목은 들어올 수 없다
 * @param plannedStopLoss 기록된 계획의 손절가. <b>앱 밖에서 연 포지션이면 비어 있다</b>
 */
public record PositionProtection(
        ExchangePosition position,
        List<ProtectiveOrder> orders,
        Optional<Price> plannedStopLoss) {

    public PositionProtection {
        DomainValues.required(position, "포지션");
        DomainValues.required(orders, "보호 주문");
        DomainValues.required(plannedStopLoss, "계획된 손절가");
        orders.forEach(order -> assertBelongsTo(order, position));
        orders = List.copyOf(orders);
    }

    /** 전체 주문 목록에서 이 포지션의 것만 골라 담는다. */
    public static PositionProtection of(
            ExchangePosition position, List<ProtectiveOrder> allOrders, Optional<Price> planned) {
        DomainValues.required(position, "포지션");
        DomainValues.required(allOrders, "보호 주문");
        return new PositionProtection(position, allOrders.stream()
                .filter(order -> order.closes() == position.direction())
                .filter(order -> order.symbol().equals(position.symbol()))
                .toList(), planned);
    }

    /** 손절로 덮인 수량. 여러 건이면 더하고 포지션 수량을 넘지 않는다. */
    public Quantity stoppedQuantity() {
        BigDecimal total = orders.stream()
                .filter(ProtectiveOrder::stopsLoss)
                .map(order -> order.coverageOf(position.quantity()).value())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return Quantity.of(total.min(position.quantity().value()).toPlainString());
    }

    public StopLossCoverage coverage() {
        BigDecimal stopped = stoppedQuantity().value();
        if (stopped.signum() == 0) {
            return StopLossCoverage.NONE;
        }
        return stopped.compareTo(position.quantity().value()) >= 0
                ? StopLossCoverage.FULL
                : StopLossCoverage.PARTIAL;
    }

    /**
     * 익절은 걸어 두었는데 손절은 없다.
     *
     * <p><b>이 조합에 따로 이름을 주는 이유</b>는 {@code exit-automation.md} § 0 의 진술이
     * 정확히 이 모양이기 때문이다 — 나가는 준비를 아예 안 한 것이 아니라, 버는 쪽만 준비하고
     * 잃는 쪽을 비워 둔 것이다. 화면이 "아무것도 안 걸려 있다" 와 다른 말을 해야 한다.
     */
    public boolean takeProfitWithoutStopLoss() {
        return coverage() == StopLossCoverage.NONE
                && orders.stream().anyMatch(order -> !order.stopsLoss());
    }

    /**
     * 덮이지 않은 수량으로 계산한, 손절이 없어서 열려 있는 위험.
     *
     * <p><b>비어 있을 수 있다</b> — 계획이 없으면 어디까지 잃을지 말할 수 없고, 전량이
     * 덮여 있으면 이 수가 0 이라 적을 것이 없다. 0 으로 채우면 "위험이 없다" 로 읽히는데
     * 계획을 모르는 것과 위험이 없는 것은 다른 사실이다.
     */
    public Optional<Money> exposureWithoutStop() {
        if (coverage() == StopLossCoverage.FULL) {
            return Optional.empty();
        }
        Quantity bare = Quantity.of(position.quantity().value()
                .subtract(stoppedQuantity().value()).toPlainString());
        return plannedStopLoss.map(stop -> bare.times(position.markPrice().absoluteDifference(stop)));
    }

    private static void assertBelongsTo(ProtectiveOrder order, ExchangePosition position) {
        if (order.closes() != position.direction()
                || !order.symbol().equals(position.symbol())) {
            throw new InvalidAccountDataException(
                    "다른 포지션의 주문은 보호로 셀 수 없다: %s %s"
                            .formatted(order.symbol().value(), order.closes()));
        }
    }
}
