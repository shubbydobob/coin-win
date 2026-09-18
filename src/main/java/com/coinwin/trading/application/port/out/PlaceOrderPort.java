package com.coinwin.trading.application.port.out;

import com.coinwin.trading.domain.OrderId;
import com.coinwin.trading.domain.OrderIntent;
import com.coinwin.trading.domain.PlacedOrder;
import com.coinwin.trading.domain.TradingMode;

/**
 * 주문을 내는 자리. <b>이 프로젝트에서 돈이 움직일 수 있는 유일한 인터페이스다.</b>
 *
 * <p>구현체가 셋이다 — 장부 · 테스트넷 · 실계좌. 앞의 둘은 돈이 들지 않고 마지막 하나만
 * 든다. 그 차이가 {@link TradingMode} 하나로 드러나고 <b>모든 기록에 실린다.</b>
 *
 * <p><b>출금 엔드포인트는 여기에 없고 앞으로도 없다.</b> {@code scope.md} 가 주문 실행만
 * 조건부로 해제했고 출금은 그대로 금지다. 키에서도 끈다 — 코드와 권한 양쪽에서 막는 것은
 * 한쪽이 무너져도 다른 쪽이 남기 위해서다.
 *
 * <p><b>안전장치를 여기서 하지 않는다.</b> {@code RiskLimits} 가 이미 판정했고, 어댑터가 또
 * 검사하면 구현체마다 다른 한계를 갖게 된다 — 감시 모듈의 호가 단수가 서비스와 인메모리에서
 * 갈려 있던 자리와 같은 종류다.
 */
public interface PlaceOrderPort {

    /** 이 브로커가 어느 모드인가. <b>기록의 모든 줄에 이 값이 붙는다.</b> */
    TradingMode mode();

    /**
     * 주문을 낸다.
     *
     * @throws com.coinwin.common.domain.ExternalDataUnavailableException 거래소가 받지 않았을 때
     */
    PlacedOrder place(OrderIntent intent);

    /**
     * 걸려 있는 주문을 취소한다. <b>없는 주문을 취소하는 것은 실패가 아니다</b> — 이미
     * 체결됐거나 이미 취소된 것이고, 어느 쪽이든 원하던 상태(그 주문이 없음)가 됐다.
     *
     * @throws com.coinwin.common.domain.ExternalDataUnavailableException 거래소를 못 불렀을 때
     */
    void cancel(OrderId id);
}
