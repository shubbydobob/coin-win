package com.coinwin.trading.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Percentage;
import com.coinwin.common.domain.Price;
import com.coinwin.position.domain.Direction;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * <b>진입은 하지 않는다. 나가는 절차를 기계가 한다.</b> R1 · R3 · R4 · R5.
 *
 * <ul>
 *   <li><b>R1</b> — 손절이 없으면 건다. 사이클마다 다시 물으므로 사람이 지우면 다시 걸린다(R2)
 *   <li><b>R3</b> — 익절이 없으면 <b>절반</b>을 목표가에 건다
 *   <li><b>R4</b> — 가격이 그 목표에 닿으면 손절을 <b>본전</b>으로 옮긴다
 *   <li><b>R5</b> — 같은 순간에 남은 절반 위로 <b>추격 손절</b>을 하나 더 건다
 * </ul>
 *
 * <p><b>R4 한 줄이 이 전략의 이유다.</b> 절반은 이미 확정됐고 남은 절반의 최악이 0 이므로
 * <b>그 거래는 손실로 끝날 수 없다</b> — "300~400달러 벌고 있다가 다 반납" 하는 경로가
 * 구조적으로 사라진다({@code docs/spec/exit-automation.md} § 0 · § 3.1).
 *
 * <p><b>R5 는 본전 손절을 대신하지 않고 더한다.</b> 대신하면 R4 의 보장이 추격 폭에 따라
 * 무너진다 — 진입가의 2% 위에서 3% 를 따라오는 손절은 <b>진입가보다 아래</b>에 놓이고,
 * 그러면 "손실로 끝날 수 없다" 가 거짓이 된다. 둘을 함께 두면 <b>언제나 위쪽이 먼저
 * 터진다</b>: 추격이 본전보다 위면 추격이, 아래면 본전이. 그래서 추격은 더 먹는 일만 하고
 * 바닥은 R4 가 그대로 지킨다. 둘 다 남아 있어도 안전한 이유는 하나가 터지면 포지션이
 * 닫히고 거래소가 나머지를 지우기 때문이다({@link CycleDecision} 이 적은 것과 같은 사실).
 *
 * <p><b>명세와 한 군데 다르다.</b> 명세의 R4 는 <i>"R3 체결을 감지하면"</i> 이고 이 구현은
 * <i>"가격이 1차 목표에 닿으면"</i> 이다. 둘은 같은 순간이지만 뒤쪽은 <b>주문 상태가 아니라
 * 가격의 함수</b>라 사이클마다 같은 답을 낸다 — 체결이 부분이었든, 사람이 익절을 지웠든,
 * 봇이 재시작했든 상관없이 성립한다. 전략이 결정론이어야 한다는 조건이 이 모양을 골랐다.
 *
 * <p><b>손절을 다시 계산하지 않는다.</b> 이미 걸려 있으면 그대로 둔다 — 사이클마다 표시가
 * 기준으로 다시 걸면 그것은 손절이 아니라 <b>의도치 않은 추격 손절</b>이 되고, 가격이 불리하게
 * 움직일 때 손절도 따라 내려간다. 옮기는 경우는 R4 하나뿐이고 그 값은 진입가에 고정돼 있다.
 * <b>진짜 추격 손절은 반대로 불리한 쪽으로 절대 움직이지 않는다</b> — 그것이 R5 와 그 고장의
 * 차이 전부다.
 *
 * @param stopDistance 진입가에서 손절까지(%). <b>자리표시자다</b> — 잰 적이 없다
 * @param firstTarget 진입가에서 1차 익절까지(%). <b>자리표시자다</b>
 * @param roundTripCost 왕복 비용(%). 본전은 진입가가 아니라 이만큼 유리한 쪽이다
 * @param trailing 추격 폭. <b>비어 있으면 R5 를 하지 않는다</b> — 거래소가 정한 허용 범위를
 *     확인하지 못했으므로 끌 수 있어야 한다({@link CallbackRate})
 */
public record ExitRuleStrategy(
        Percentage stopDistance,
        Percentage firstTarget,
        Percentage roundTripCost,
        Optional<CallbackRate> trailing)
        implements TradingStrategy {

    /** 1차 익절이 가져가는 비중. {@code scope.md} 의 매매 전제가 이미 50% 분할이다. */
    private static final Percentage HALF = Percentage.of("50");

    public ExitRuleStrategy {
        DomainValues.required(stopDistance, "손절 거리");
        DomainValues.required(firstTarget, "1차 익절 거리");
        DomainValues.required(roundTripCost, "왕복 비용");
        DomainValues.required(trailing, "추격 폭");
        if (stopDistance.value().signum() <= 0 || firstTarget.value().signum() <= 0) {
            throw new InvalidOrderException("손절·익절 거리는 0 보다 커야 한다");
        }
    }

    /** 추격 없이 R1 · R3 · R4 만. */
    public ExitRuleStrategy(
            Percentage stopDistance, Percentage firstTarget, Percentage roundTripCost) {
        this(stopDistance, firstTarget, roundTripCost, Optional.empty());
    }

    @Override
    public String name() {
        return "청산 규칙 (진입 안 함 · 손절 %s%% · 절반 익절 %s%%%s)".formatted(
                stopDistance.value().toPlainString(), firstTarget.value().toPlainString(),
                trailing.map(rate -> " · 추격 " + rate.describe()).orElse(""));
    }

    @Override
    public CycleDecision decide(BotContext now) {
        DomainValues.required(now, "사이클 컨텍스트");
        return now.open().map(open -> forPosition(now, open)).orElseGet(CycleDecision::none);
    }

    private CycleDecision forPosition(BotContext now, BotPosition open) {
        boolean reached = prices().reached(now.view().mark(), open);
        List<OrderIntent> place = new ArrayList<>();
        List<OrderId> cancel = new ArrayList<>();
        stopWork(now, open, reached).ifPresent(work -> {
            place.add(work.intent());
            cancel.addAll(work.replaces());
        });
        if (!reached && !has(now, OrderKind.TAKE_PROFIT)) {
            place.add(takeProfit(now, open));
        }
        if (reached && !has(now, OrderKind.TRAILING_STOP)) {
            trailing.map(rate -> trail(now, open, rate)).ifPresent(place::add);
        }
        return new CycleDecision(place, cancel);
    }

    /**
     * 손절에 대해 할 일. 없으면 걸고, R4 에 닿았는데 아직 본전이 아니면 옮긴다.
     *
     * <p>옮기는 것은 <b>새로 걸고 옛것을 지우는</b> 두 걸음이다. 서비스가 그 순서를 지킨다.
     */
    private Optional<StopWork> stopWork(BotContext now, BotPosition open, boolean reached) {
        Price desired = reached
                ? prices().breakEven(open)
                : prices().initialStop(now.view().mark(), open);
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

    private ExitPrices prices() {
        return new ExitPrices(stopDistance, firstTarget, roundTripCost);
    }

    private OrderIntent takeProfit(BotContext now, BotPosition open) {
        return OrderIntent.protectPart(now.view().symbol(), open.direction(),
                new OrderIntent.Protection(
                        OrderKind.TAKE_PROFIT, prices().target(open), now.view().mark()),
                HALF.applyTo(open.quantity()));
    }

    /**
     * <b>R5.</b> 남은 수량 위로 추격 손절을 건다.
     *
     * <p>수량은 <b>지금 열려 있는 만큼</b>이다 — 1차 익절이 이미 체결됐으면 절반이고, 아직이면
     * 전량이다. 뒤쪽이어도 안전한 이유는 이 주문이 줄이기만 하기 때문이다({@code reduceOnly}):
     * 남은 것보다 큰 수량이 실려도 포지션이 뒤집히지 않고 있는 만큼만 닫힌다.
     */
    private OrderIntent trail(BotContext now, BotPosition open, CallbackRate rate) {
        return OrderIntent.trail(now.view().symbol(), open.direction(),
                new OrderIntent.Trailing(rate, open.quantity(), now.view().mark()));
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

    private static boolean has(BotContext now, OrderKind kind) {
        return now.resting().stream().anyMatch(order -> order.intent().kind() == kind);
    }

    private static boolean allAt(List<PlacedOrder> orders, Price price) {
        return orders.stream().allMatch(order -> price.equals(order.intent().trigger().orElse(null)));
    }
}
