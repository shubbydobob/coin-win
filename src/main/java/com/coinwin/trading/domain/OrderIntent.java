package com.coinwin.trading.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import com.coinwin.market.domain.Symbol;
import com.coinwin.position.domain.Direction;
import java.util.Optional;

/**
 * 봇이 내려는 주문 하나. <b>아직 낸 것이 아니다.</b>
 *
 * <p>의도와 체결을 타입으로 가른다. 전략이 만드는 것은 의도이고, 안전장치를 통과한 것만
 * 브로커로 간다 — 한 타입으로 두면 "거부된 주문" 과 "낸 주문" 이 같은 값이 되고, 거부가
 * 기록에서 사라진다.
 *
 * <p><b>칸이 하나 늘었다(추격 폭).</b> 세 칸({@code quantity} · {@code trigger} ·
 * {@code callbackRate})이 전부 비어 있을 수 있지만 <b>어느 조합이 성립하는지는 종류가
 * 정한다</b> — 그 검사가 생성자에 있으므로 말이 안 되는 주문은 만들어지지도 않는다.
 *
 * @param symbol 종목
 * @param position 이 주문이 만들거나 줄이는 <b>포지션의</b> 방향. 매수/매도가 아니다 —
 *     롱을 닫는 매도와 숏을 여는 매도가 같은 값이 되면 안 된다
 * @param kind 무엇을 하는 주문인가
 * @param quantity 수량. <b>비어 있으면 전량</b>({@code closePosition}). 줄이는 주문만 비울 수
 *     있다 — 열 때는 얼마를 여는지 반드시 말해야 한다
 * @param trigger 트리거 가격. 값이 미리 정해지는 트리거 주문만 갖는다
 * @param callbackRate 추격 폭. 추격 손절만 갖는다
 * @param reference 명목을 재고 장부가 체결가를 정하는 기준가. <b>표시가를 쓴다</b> —
 *     청산이 트리거되는 값이고 위험의 크기도 그것으로 잰다
 */
public record OrderIntent(
        Symbol symbol,
        Direction position,
        OrderKind kind,
        Optional<Quantity> quantity,
        Optional<Price> trigger,
        Optional<CallbackRate> callbackRate,
        Price reference) {

    public OrderIntent {
        DomainValues.required(symbol, "종목");
        DomainValues.required(position, "포지션 방향");
        DomainValues.required(kind, "주문 종류");
        DomainValues.required(quantity, "수량");
        DomainValues.required(trigger, "트리거 가격");
        DomainValues.required(callbackRate, "추격 폭");
        DomainValues.required(reference, "기준가");
        assertTriggerMatches(kind, trigger);
        assertCallbackMatches(kind, callbackRate);
        assertQuantityMatches(kind, quantity);
    }

    /** 시장가 진입. 수량을 반드시 말한다. */
    public static OrderIntent entry(Symbol symbol, Direction position, Sizing sizing) {
        DomainValues.required(sizing, "수량과 기준가");
        return new OrderIntent(symbol, position, OrderKind.ENTRY,
                Optional.of(sizing.quantity()), Optional.empty(), Optional.empty(),
                sizing.reference());
    }

    /** 전량을 닫는 트리거 주문. 수량이 어긋나도 남는 포지션이 없다. */
    public static OrderIntent protectAll(Symbol symbol, Direction position, Protection protection) {
        DomainValues.required(protection, "종류와 트리거");
        return new OrderIntent(symbol, position, protection.kind(),
                Optional.empty(), Optional.of(protection.trigger()), Optional.empty(),
                protection.reference());
    }

    /** 일부만 닫는 트리거 주문. R3 의 절반 익절이 이것이다. */
    public static OrderIntent protectPart(
            Symbol symbol, Direction position, Protection protection, Quantity part) {
        DomainValues.required(protection, "종류와 트리거");
        DomainValues.required(part, "수량");
        return new OrderIntent(symbol, position, protection.kind(),
                Optional.of(part), Optional.of(protection.trigger()), Optional.empty(),
                protection.reference());
    }

    /**
     * 추격 손절. <b>수량을 반드시 말한다.</b>
     *
     * <p>{@code closePosition} 은 {@code STOP_MARKET} · {@code TAKE_PROFIT_MARKET} 에만 쓸 수
     * 있는 것으로 <b>읽었고 확인하지 못했다</b>(네트워크 정책이 바이낸스를 막는다). 수량을
     * 명시하는 쪽은 어느 쪽이 맞든 받아들여지고, {@code reduceOnly} 가 붙으므로 수량이
     * 남은 것보다 커도 포지션이 뒤집히지 않는다 — <b>모르는 쪽을 고를 때 안전한 쪽을 고른다.</b>
     */
    public static OrderIntent trail(Symbol symbol, Direction position, Trailing trailing) {
        DomainValues.required(trailing, "추격 폭과 수량");
        return new OrderIntent(symbol, position, OrderKind.TRAILING_STOP,
                Optional.of(trailing.quantity()), Optional.empty(),
                Optional.of(trailing.rate()), trailing.reference());
    }

    /** 지금 당장 전량 시장가로 닫는다. */
    public static OrderIntent closeNow(Symbol symbol, Direction position, Price reference) {
        return new OrderIntent(symbol, position, OrderKind.EXIT,
                Optional.empty(), Optional.empty(), Optional.empty(), reference);
    }

    /** 진입 수량과 그것을 정한 기준가. 둘이 함께 다녀야 명목이 재진다. */
    public record Sizing(Quantity quantity, Price reference) {
        public Sizing {
            DomainValues.required(quantity, "수량");
            DomainValues.required(reference, "기준가");
        }
    }

    /** 보호 주문의 종류와 트리거. */
    public record Protection(OrderKind kind, Price trigger, Price reference) {
        public Protection {
            DomainValues.required(kind, "주문 종류");
            DomainValues.required(trigger, "트리거 가격");
            DomainValues.required(reference, "기준가");
        }
    }

    /** 추격 손절이 따라오는 폭과 따라올 수량. */
    public record Trailing(CallbackRate rate, Quantity quantity, Price reference) {
        public Trailing {
            DomainValues.required(rate, "추격 폭");
            DomainValues.required(quantity, "수량");
            DomainValues.required(reference, "기준가");
        }
    }

    /** 전량을 닫는가. */
    public boolean closesEntirePosition() {
        return quantity.isEmpty();
    }

    /**
     * 이 주문이 만드는 명목. <b>수량이 아니라 이것이 위험의 크기다.</b>
     *
     * <p>줄이는 주문은 0 을 낸다 — 위험을 만들지 않으므로 한계에 세지 않는다.
     */
    public Money notional() {
        if (kind.reducesPosition()) {
            return Money.of("0");
        }
        return quantity.orElseThrow().times(reference.asAmount());
    }

    private static void assertTriggerMatches(OrderKind kind, Optional<Price> trigger) {
        if (kind.needsTrigger() != trigger.isPresent()) {
            throw new InvalidOrderException(
                    "%s 주문의 트리거 가격이 맞지 않는다".formatted(kind));
        }
    }

    private static void assertCallbackMatches(OrderKind kind, Optional<CallbackRate> rate) {
        if (kind.needsCallbackRate() != rate.isPresent()) {
            throw new InvalidOrderException("%s 주문의 추격 폭이 맞지 않는다".formatted(kind));
        }
    }

    private static void assertQuantityMatches(OrderKind kind, Optional<Quantity> quantity) {
        if (!kind.reducesPosition() && quantity.isEmpty()) {
            throw new InvalidOrderException("포지션을 여는 주문은 수량을 말해야 한다");
        }
        if (kind.needsCallbackRate() && quantity.isEmpty()) {
            throw new InvalidOrderException("추격 손절은 수량을 말해야 한다 — 전량 표시를 쓸 수 없다");
        }
        if (quantity.isPresent() && quantity.get().value().signum() <= 0) {
            throw new InvalidOrderException("주문 수량은 0 보다 커야 한다. 전량은 비워 둔다");
        }
    }
}
