package com.coinwin.account.application.port.out;

import com.coinwin.account.domain.ProtectiveOrder;
import com.coinwin.market.domain.Symbol;
import java.util.List;

/**
 * 거래소에 걸려 있는 <b>포지션을 닫는</b> 미체결 주문.
 *
 * <p>구현체가 둘이다 — 서명해서 거래소를 때리는 것과 인메모리.
 * {@link LoadExchangePositionsPort} 와 같은 자리이고 같은 이유다.
 *
 * <p><b>포지션을 여는 주문은 돌려주지 않는다.</b> 진입 지정가가 섞이면 "손절이 걸려 있다" 는
 * 판정이 통과해 버리고, 그것이 정확히 이 기능이 막으려는 상태다. 거르는 규칙이 구현체마다
 * 갈리지 않도록 포트의 계약으로 못 박는다 — 감시 모듈의 호가 단수가 서비스와 인메모리에서
 * 갈려 있던 것을 계약 테스트가 지나갔던 자리와 같은 종류다.
 *
 * <p><b>주문을 내지 않는다.</b> 이 포트에는 읽는 메서드만 있고, 키도 읽기 전용으로 발급해야
 * 한다({@code scope.md}). 거는 쪽은 {@code docs/spec/exit-automation.md} § 5 의 3단계이고
 * 그때 {@code scope.md} 를 먼저 고친다.
 */
public interface LoadOpenOrdersPort {

    /**
     * 이 종목에 걸려 있는 포지션 종료 주문. 없으면 빈 목록이다.
     *
     * @throws com.coinwin.common.domain.ExternalDataUnavailableException 거래소를 읽지 못했을 때
     */
    List<ProtectiveOrder> protectiveOrdersFor(Symbol symbol);
}
