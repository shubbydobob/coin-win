package com.coinwin.account.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import com.coinwin.market.domain.Symbol;
import com.coinwin.position.domain.Direction;
import java.util.Optional;

/**
 * 거래소에 걸려 있는 <b>포지션을 닫는</b> 미체결 주문.
 *
 * <p>포지션을 <b>여는</b> 주문은 이 타입이 아니다. 담으면 "손절이 있다" 는 판정에 진입 지정가가
 * 섞이고, 그것은 이 기능이 막으려는 상태를 정확히 통과시킨다.
 *
 * <p><b>방향은 "무엇을 닫는가" 다.</b> 거래소가 주는 것은 매수/매도인데 그대로 담으면 롱을
 * 닫는 매도와 숏을 여는 매도가 같은 값이 된다. 변환은 어댑터에서 끝낸다 —
 * {@link ExchangePosition} 이 부호를 방향으로 바꿔 담는 것과 같은 판단이다.
 *
 * @param symbol 종목
 * @param closes 이 주문이 닫는 포지션의 방향
 * @param kind 손절인가 익절인가
 * @param triggerPrice 트리거 가격. <b>추격 손절만 비어 있을 수 있다</b> — 고점을 따라
 *     움직이므로 미리 정해진 값이 없다. 그 밖의 종류에서 비어 있으면 주문이 아니다
 * @param quantity 닫을 수량. <b>비어 있으면 전량이다</b>({@code closePosition=true}).
 *     0 을 담아 "전량" 을 뜻하게 하지 않는다 — 없는 것과 0 은 다른 사실이다
 */
public record ProtectiveOrder(
        Symbol symbol,
        Direction closes,
        ProtectiveOrderKind kind,
        Optional<Price> triggerPrice,
        Optional<Quantity> quantity) {

    public ProtectiveOrder {
        DomainValues.required(symbol, "종목");
        DomainValues.required(closes, "닫는 방향");
        DomainValues.required(kind, "주문 종류");
        DomainValues.required(triggerPrice, "트리거 가격");
        DomainValues.required(quantity, "수량");
        assertTriggerIsKnown(kind, triggerPrice);
        assertQuantityIsPositive(quantity);
    }

    /** 전량을 닫는 주문. 바이낸스 {@code closePosition=true} 가 이것이다. */
    public static ProtectiveOrder entirePosition(
            Symbol symbol, Direction closes, ProtectiveOrderKind kind, Price triggerPrice) {
        return new ProtectiveOrder(
                symbol, closes, kind, Optional.ofNullable(triggerPrice), Optional.empty());
    }

    /** 수량을 정해 닫는 주문. */
    public static ProtectiveOrder partial(
            Symbol symbol, Direction closes, ProtectiveOrderKind kind, PartialSize size) {
        DomainValues.required(size, "수량과 트리거");
        return new ProtectiveOrder(symbol, closes, kind,
                Optional.ofNullable(size.triggerPrice()), Optional.of(size.quantity()));
    }

    /**
     * 부분 청산 주문의 트리거와 수량.
     *
     * <p>{@link #partial} 의 인자를 넷으로 묶기 위한 파라미터 객체다 — 다섯 번째 인자를 두면
     * Checkstyle 의 파라미터 한계(4)에 걸린다. 그 한계가 겨냥하는 것이 바로 이런 자리다.
     */
    public record PartialSize(Price triggerPrice, Quantity quantity) {
        public PartialSize {
            DomainValues.required(quantity, "수량");
        }
    }

    /** 수량을 정하지 않고 전량을 닫는가. */
    public boolean closesEntirePosition() {
        return quantity.isEmpty();
    }

    /** 손실을 자르는 주문인가. */
    public boolean stopsLoss() {
        return kind.stopsLoss();
    }

    /**
     * 이 주문이 실제로 덮는 수량.
     *
     * <p>전량 주문이면 포지션 수량 그대로다. 수량이 포지션보다 크면 포지션 수량으로 자른다 —
     * 남는 만큼은 닫을 것이 없으므로 보호를 두 번 세면 안 된다.
     */
    public Quantity coverageOf(Quantity positionQuantity) {
        DomainValues.required(positionQuantity, "포지션 수량");
        return quantity
                .filter(ordered -> ordered.value().compareTo(positionQuantity.value()) < 0)
                .orElse(positionQuantity);
    }

    private static void assertTriggerIsKnown(
            ProtectiveOrderKind kind, Optional<Price> triggerPrice) {
        if (triggerPrice.isEmpty() && kind != ProtectiveOrderKind.TRAILING_STOP) {
            throw new InvalidAccountDataException(
                    "트리거 가격이 없는 %s 주문은 성립하지 않는다".formatted(kind));
        }
    }

    private static void assertQuantityIsPositive(Optional<Quantity> quantity) {
        if (quantity.isPresent() && quantity.get().value().signum() <= 0) {
            throw new InvalidAccountDataException(
                    "주문 수량은 0 보다 커야 한다. 전량을 뜻하려면 비워 둔다");
        }
    }
}
