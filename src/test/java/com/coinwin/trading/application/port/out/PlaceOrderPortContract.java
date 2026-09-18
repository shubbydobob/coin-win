package com.coinwin.trading.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;

import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import com.coinwin.market.domain.Symbol;
import com.coinwin.position.domain.Direction;
import com.coinwin.trading.domain.CallbackRate;
import com.coinwin.trading.domain.OrderIntent;
import com.coinwin.trading.domain.OrderKind;
import com.coinwin.trading.domain.PlacedOrder;
import org.junit.jupiter.api.Test;

/**
 * 주문 포트의 계약. <b>장부 브로커와 거래소 어댑터가 모두 이 스위트를 통과해야 한다.</b>
 *
 * <p>어댑터마다 테스트를 따로 쓰면 각자 자기 구현이 하는 일을 검사하게 되고, 포트가 하나의
 * 약속인지는 아무도 확인하지 않는다. 감시 모듈에서 실제로 그랬다 — 두 구현이 다르게 행동해도
 * 스위트가 통과하는 상태였다.
 *
 * <p><b>이 포트에서 그 위험이 가장 크다.</b> 장부에서 되던 것이 실계좌에서 다르게 동작하면
 * 그 차이는 돈으로 드러난다.
 *
 * <p><b>거래소를 때리지 않는다.</b> 주문은 서명이 필요하고, 무엇보다 <b>진짜로 주문이
 * 나가면 안 된다.</b> 거래소 쪽은 로컬 가짜 서버에 응답 원문을 물려 확인한다 — 증명되지
 * 않는 것은 그 원문이 진짜 거래소와 같은가 하나뿐이고, 그것은 테스트넷에 사람이 한 번
 * 붙여 보는 것 말고 방법이 없다.
 */
public abstract class PlaceOrderPortContract {

    protected static final Symbol SYMBOL = Symbol.BTC_USDT;
    protected static final Price MARK = Price.of("78000");

    protected abstract PlaceOrderPort port();

    /** 모드를 모르는 주문이 있으면 안 된다. 장부 기록과 실계좌 기록이 한 표에 섞인다. */
    @Test
    void 낸_주문에_모드가_실린다() {
        PlacedOrder placed = port().place(entry());

        assertThat(placed.mode()).isEqualTo(port().mode());
    }

    @Test
    void 시장가_주문은_체결가를_갖는다() {
        PlacedOrder placed = port().place(entry());

        assertThat(placed.resting()).isFalse();
        assertThat(placed.fillPrice()).isPresent();
    }

    /** 0 으로 채우면 "0 원에 체결됐다" 가 되고 그 수가 손익 계산에 들어간다. */
    @Test
    void 트리거_주문은_걸려만_있고_체결가가_없다() {
        PlacedOrder placed = port().place(stop());

        assertThat(placed.resting()).isTrue();
        assertThat(placed.fillPrice()).isEmpty();
    }

    /**
     * 추격 손절도 걸려만 있다. <b>트리거 가격이 없다고 시장가로 읽으면</b> 두 구현 중
     * 한쪽이 그 자리에서 포지션을 닫는다 — 감시 화면이 호가 단수에서 배운 것과 같은 종류의
     * 갈라짐이고, 여기서는 그 대가가 돈이다.
     */
    @Test
    void 추격_손절도_걸려만_있고_체결가가_없다() {
        PlacedOrder placed = port().place(trailing());

        assertThat(placed.resting()).isTrue();
        assertThat(placed.intent().trigger()).isEmpty();
        assertThat(placed.intent().callbackRate()).isPresent();
    }

    /** 취소할 때 이것으로 가리킨다. 비어 있으면 지울 수 없다. */
    @Test
    void 낸_주문은_식별자를_갖는다() {
        assertThat(port().place(stop()).id().value()).isNotBlank();
    }

    @Test
    void 주문마다_다른_식별자를_준다() {
        assertThat(port().place(stop()).id()).isNotEqualTo(port().place(stop()).id());
    }

    /** 이미 없는 주문을 지우는 것은 실패가 아니다 — 원하던 상태가 이미 됐다. */
    @Test
    void 같은_주문을_두_번_취소해도_던지지_않는다() {
        PlacedOrder placed = port().place(stop());

        port().cancel(placed.id());
        port().cancel(placed.id());
    }

    /** 낸 주문은 의도를 그대로 들고 있다. 잃으면 나중에 무엇을 냈는지 알 수 없다. */
    @Test
    void 낸_주문은_무엇을_내려던_것인지_기억한다() {
        OrderIntent intent = stop();

        assertThat(port().place(intent).intent()).isEqualTo(intent);
    }

    protected static OrderIntent entry() {
        return OrderIntent.entry(SYMBOL, Direction.LONG,
                new OrderIntent.Sizing(Quantity.of("0.01"), MARK));
    }

    protected static OrderIntent stop() {
        return OrderIntent.protectAll(SYMBOL, Direction.LONG,
                new OrderIntent.Protection(OrderKind.STOP_LOSS, Price.of("76440"), MARK));
    }

    protected static OrderIntent exit() {
        return OrderIntent.closeNow(SYMBOL, Direction.LONG, MARK);
    }

    /**
     * 추격 손절. <b>걸려만 있으면서 트리거 가격이 없는 유일한 주문</b>이라 두 구현이
     * 갈라지기 가장 쉬운 자리다 — 한쪽이 이것을 시장가로 읽으면 포지션이 그 자리에서 닫힌다.
     */
    protected static OrderIntent trailing() {
        return OrderIntent.trail(SYMBOL, Direction.LONG, new OrderIntent.Trailing(
                CallbackRate.of("1"), Quantity.of("0.01"), MARK));
    }
}
