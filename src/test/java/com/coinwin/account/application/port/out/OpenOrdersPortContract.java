package com.coinwin.account.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;

import com.coinwin.account.domain.ProtectiveOrder;
import com.coinwin.account.domain.ProtectiveOrderKind;
import com.coinwin.market.domain.Symbol;
import com.coinwin.position.domain.Direction;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 미체결 주문 포트의 계약. <b>바이낸스 어댑터와 인메모리 어댑터가 모두 이 스위트를 통과해야
 * 한다.</b>
 *
 * <p>어댑터마다 테스트를 따로 쓰면 각자 자기 구현이 하는 일을 검사하게 되고, 포트가 하나의
 * 약속인지는 아무도 확인하지 않는다. 감시 모듈에서 실제로 그랬다 — 서비스는 호가 단수를
 * "1 과 20 사이" 로 검사하는데 거래소는 3 을 거절했고, <b>계약 테스트가 5 와 20 만 써서
 * 지나갔다.</b>
 *
 * <p>두 구현이 같은 장면을 놓고 답해야 한다. 그래서 스위트가 장면을 정하고 구현은 그것을
 * 자기 방식으로 차린다 — 바이낸스는 응답 JSON 으로, 인메모리는 도메인 객체로.
 *
 * <p><b>거래소를 때리지 않는다.</b> 이 포트는 서명이 필요해 실계좌 없이는 실제 응답을 볼 수
 * 없고, 그러면 {@code check} 가 키에 묶인다. 바이낸스 쪽은 로컬 가짜 서버에 <b>거래소 응답
 * 원문</b>을 물려 매핑을 확인한다 — 증명되지 않는 것은 그 원문이 진짜 거래소와 같은가
 * 하나뿐이고, 그것은 실계좌로 사람이 한 번 보는 것 말고 방법이 없다.
 */
public abstract class OpenOrdersPortContract {

    protected static final Symbol SYMBOL = Symbol.BTC_USDT;

    /**
     * 아래 장면이 차려진 어댑터.
     *
     * <ul>
     *   <li>롱 전량 손절 — 트리거 58000, 수량 없음(전량)
     *   <li>롱 부분 익절 — 트리거 64000, 수량 0.05
     *   <li>숏 추격 손절 — 트리거 없음, 수량 없음
     *   <li>다른 종목(ETHUSDT)의 롱 전량 손절 — 이 종목을 물으면 나오면 안 된다
     * </ul>
     */
    protected abstract LoadOpenOrdersPort port();

    @Test
    void 이_종목의_종료_주문만_돌려준다() {
        assertThat(port().protectiveOrdersFor(SYMBOL))
                .hasSize(3)
                .allSatisfy(order -> assertThat(order.symbol()).isEqualTo(SYMBOL));
    }

    /** 전량 주문은 수량을 갖지 않는다. 0 으로 담으면 "0 만큼 덮는다" 가 된다. */
    @Test
    void 전량_손절은_수량이_비어_있다() {
        ProtectiveOrder stop = only(ProtectiveOrderKind.STOP_LOSS);

        assertThat(stop.closesEntirePosition()).isTrue();
        assertThat(stop.triggerPrice()).map(price -> price.value().toPlainString())
                .contains("58000.00");
    }

    /** 매도 전량 손절은 <b>롱</b>을 닫는다. 매수/매도를 그대로 담으면 이 사실이 사라진다. */
    @Test
    void 손절과_익절은_닫는_방향을_안다() {
        assertThat(only(ProtectiveOrderKind.STOP_LOSS).closes()).isEqualTo(Direction.LONG);
        assertThat(only(ProtectiveOrderKind.TAKE_PROFIT).closes()).isEqualTo(Direction.LONG);
        assertThat(only(ProtectiveOrderKind.TRAILING_STOP).closes()).isEqualTo(Direction.SHORT);
    }

    @Test
    void 부분_익절은_수량을_갖는다() {
        ProtectiveOrder takeProfit = only(ProtectiveOrderKind.TAKE_PROFIT);

        assertThat(takeProfit.closesEntirePosition()).isFalse();
        assertThat(takeProfit.quantity()).map(amount -> amount.value().toPlainString())
                .contains("0.05000000");
    }

    /** 추격 손절은 고점을 따라 움직이므로 트리거가 미리 정해져 있지 않다. */
    @Test
    void 추격_손절은_트리거가_비어_있고_손절로_센다() {
        ProtectiveOrder trailing = only(ProtectiveOrderKind.TRAILING_STOP);

        assertThat(trailing.triggerPrice()).isEmpty();
        assertThat(trailing.stopsLoss()).isTrue();
    }

    /** 모르는 종목은 조용히 빈 목록이다. 주문이 없는 것과 같은 사실이므로 던지지 않는다. */
    @Test
    void 주문이_없는_종목은_빈_목록이다() {
        assertThat(port().protectiveOrdersFor(Symbol.of("SOLUSDT"))).isEmpty();
    }

    private ProtectiveOrder only(ProtectiveOrderKind kind) {
        List<ProtectiveOrder> found = port().protectiveOrdersFor(SYMBOL).stream()
                .filter(order -> order.kind() == kind).toList();
        assertThat(found).describedAs("장면에는 %s 가 하나 있어야 한다", kind).hasSize(1);
        return found.getFirst();
    }
}
