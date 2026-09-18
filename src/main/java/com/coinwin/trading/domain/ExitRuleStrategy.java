package com.coinwin.trading.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Percentage;
import com.coinwin.common.domain.Price;
import com.coinwin.position.domain.Direction;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * <b>진입은 하지 않는다. 나가는 절차를 기계가 한다.</b> R1 · R3 · R4.
 *
 * <ul>
 *   <li><b>R1</b> — 손절이 없으면 건다. 사이클마다 다시 물으므로 사람이 지우면 다시 걸린다(R2)
 *   <li><b>R3</b> — 익절이 없으면 <b>절반</b>을 목표가에 건다
 *   <li><b>R4</b> — 가격이 그 목표에 닿으면 손절을 <b>본전</b>으로 옮긴다
 * </ul>
 *
 * <p><b>R4 한 줄이 이 전략의 이유다.</b> 절반은 이미 확정됐고 남은 절반의 최악이 0 이므로
 * <b>그 거래는 손실로 끝날 수 없다</b> — "300~400달러 벌고 있다가 다 반납" 하는 경로가
 * 구조적으로 사라진다({@code docs/spec/exit-automation.md} § 0 · § 3.1).
 *
 * <p><b>명세와 한 군데 다르다.</b> 명세의 R4 는 <i>"R3 체결을 감지하면"</i> 이고 이 구현은
 * <i>"가격이 1차 목표에 닿으면"</i> 이다. 둘은 같은 순간이지만 뒤쪽은 <b>주문 상태가 아니라
 * 가격의 함수</b>라 사이클마다 같은 답을 낸다 — 체결이 부분이었든, 사람이 익절을 지웠든,
 * 봇이 재시작했든 상관없이 성립한다. 전략이 결정론이어야 한다는 조건이 이 모양을 골랐다.
 *
 * <p><b>손절을 다시 계산하지 않는다.</b> 이미 걸려 있으면 그대로 둔다 — 사이클마다 표시가
 * 기준으로 다시 걸면 그것은 손절이 아니라 <b>의도치 않은 추격 손절</b>이 되고, 가격이 불리하게
 * 움직일 때 손절도 따라 내려간다. 옮기는 경우는 R4 하나뿐이고 그 값은 진입가에 고정돼 있다.
 *
 * <p><b>R5(추격)는 없다.</b> {@code callbackRate} 의 허용 범위를 확인하지 않았고
 * ({@code exit-automation.md} § 8), 거래소가 정한 것을 상상으로 채우면 감시 화면의 호가 단수
 * 때처럼 거절만 받는다.
 *
 * @param stopDistance 진입가에서 손절까지(%). <b>자리표시자다</b> — 잰 적이 없다
 * @param firstTarget 진입가에서 1차 익절까지(%). <b>자리표시자다</b>
 * @param roundTripCost 왕복 비용(%). 본전은 진입가가 아니라 이만큼 유리한 쪽이다
 */
public record ExitRuleStrategy(
        Percentage stopDistance, Percentage firstTarget, Percentage roundTripCost)
        implements TradingStrategy {

    /** 1차 익절이 가져가는 비중. {@code scope.md} 의 매매 전제가 이미 50% 분할이다. */
    private static final Percentage HALF = Percentage.of("50");

    public ExitRuleStrategy {
        DomainValues.required(stopDistance, "손절 거리");
        DomainValues.required(firstTarget, "1차 익절 거리");
        DomainValues.required(roundTripCost, "왕복 비용");
        if (stopDistance.value().signum() <= 0 || firstTarget.value().signum() <= 0) {
            throw new InvalidOrderException("손절·익절 거리는 0 보다 커야 한다");
        }
    }

    @Override
    public String name() {
        return "청산 규칙 (진입 안 함 · 손절 %s%% · 절반 익절 %s%%)".formatted(
                stopDistance.value().toPlainString(), firstTarget.value().toPlainString());
    }

    @Override
    public CycleDecision decide(BotContext now) {
        DomainValues.required(now, "사이클 컨텍스트");
        return now.open().map(open -> forPosition(now, open)).orElseGet(CycleDecision::none);
    }

    private CycleDecision forPosition(BotContext now, BotPosition open) {
        boolean reached = reachedFirstTarget(now.view().mark(), open);
        List<OrderIntent> place = new ArrayList<>();
        List<OrderId> cancel = new ArrayList<>();
        stopWork(now, open, reached).ifPresent(work -> {
            place.add(work.intent());
            cancel.addAll(work.replaces());
        });
        if (!reached && !hasTakeProfit(now)) {
            place.add(takeProfit(now, open));
        }
        return new CycleDecision(place, cancel);
    }

    /**
     * 손절에 대해 할 일. 없으면 걸고, R4 에 닿았는데 아직 본전이 아니면 옮긴다.
     *
     * <p>옮기는 것은 <b>새로 걸고 옛것을 지우는</b> 두 걸음이다. 서비스가 그 순서를 지킨다.
     */
    private Optional<StopWork> stopWork(BotContext now, BotPosition open, boolean reached) {
        Price desired = reached ? breakEven(open) : initialStop(now.view().mark(), open);
        List<PlacedOrder> existing = stops(now, open.direction());
        if (existing.isEmpty()) {
            return Optional.of(new StopWork(stopIntent(now, open, desired), List.of()));
        }
        if (!reached || allAt(existing, desired)) {
            return Optional.empty();
        }
        return Optional.of(new StopWork(stopIntent(now, open, desired),
                existing.stream().map(PlacedOrder::id).toList()));
    }

    /** 걸 손절과, 그것이 대신하는 옛 손절들. */
    private record StopWork(OrderIntent intent, List<OrderId> replaces) {
    }

    /**
     * 처음 거는 손절. <b>진입가 기준이다</b> — 표시가 기준으로 두면 사이클마다 값이 달라져
     * 의도치 않은 추격 손절이 된다.
     *
     * <p>다만 이미 그만큼 밀린 포지션이면 그 값이 표시가 너머라 <b>거는 즉시 체결된다</b> —
     * 그것은 손절이 아니라 시장가 청산이고 사람이 원한 적 없는 매매다. 그때만 표시가에서 잰다.
     */
    private Price initialStop(Price mark, BotPosition open) {
        Price fromEntry = away(open.entry(), open.direction(), stopDistance);
        boolean alreadyPast = open.direction() == Direction.LONG
                ? !mark.isAbove(fromEntry)
                : !mark.isBelow(fromEntry);
        return alreadyPast ? away(mark, open.direction(), stopDistance) : fromEntry;
    }

    /**
     * 본전. <b>진입가가 아니라 왕복 비용만큼 유리한 쪽이다</b> — 진입가에 걸면 수수료만큼
     * 지고 끝난다. 명세가 "진입가(+수수료)" 라고 적은 자리다.
     */
    private Price breakEven(BotPosition open) {
        return toward(open.entry(), open.direction(), roundTripCost);
    }

    private boolean reachedFirstTarget(Price mark, BotPosition open) {
        Price target = toward(open.entry(), open.direction(), firstTarget);
        return open.direction() == Direction.LONG ? !mark.isBelow(target) : !mark.isAbove(target);
    }

    private OrderIntent takeProfit(BotContext now, BotPosition open) {
        return new OrderIntent(now.view().symbol(), open.direction(), OrderKind.TAKE_PROFIT,
                Optional.of(HALF.applyTo(open.quantity())),
                Optional.of(toward(open.entry(), open.direction(), firstTarget)),
                now.view().mark());
    }

    private static OrderIntent stopIntent(BotContext now, BotPosition open, Price trigger) {
        return OrderIntent.protectAll(now.view().symbol(), open.direction(),
                new OrderIntent.Protection(OrderKind.STOP_LOSS, trigger, now.view().mark()));
    }

    private static List<PlacedOrder> stops(BotContext now, Direction direction) {
        return now.resting().stream()
                .filter(order -> order.intent().kind() == OrderKind.STOP_LOSS)
                .filter(order -> order.intent().position() == direction)
                .toList();
    }

    private static boolean hasTakeProfit(BotContext now) {
        return now.resting().stream()
                .anyMatch(order -> order.intent().kind() == OrderKind.TAKE_PROFIT);
    }

    private static boolean allAt(List<PlacedOrder> orders, Price price) {
        return orders.stream().allMatch(order -> price.equals(order.intent().trigger().orElse(null)));
    }

    /** 불리한 쪽 — 롱이면 아래, 숏이면 위. */
    private static Price away(Price from, Direction direction, Percentage distance) {
        Money offset = distance.applyTo(from.asAmount());
        return direction == Direction.LONG ? from.minus(offset) : from.plus(offset);
    }

    /** 유리한 쪽 — 롱이면 위, 숏이면 아래. */
    private static Price toward(Price from, Direction direction, Percentage distance) {
        Money offset = distance.applyTo(from.asAmount());
        return direction == Direction.LONG ? from.plus(offset) : from.minus(offset);
    }
}
