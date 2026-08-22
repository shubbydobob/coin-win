package com.coinwin.account.application.port.out;

import com.coinwin.account.domain.ExchangePosition;
import com.coinwin.market.domain.Symbol;
import java.util.List;

/**
 * 거래소가 말하는 지금 열려 있는 포지션.
 *
 * <p>구현체가 둘이다 — 서명해서 거래소를 때리는 것과 인메모리. ADR 002 의 기준("구현체가 둘
 * 이상 존재하는 경우에만 포트")을 그대로 만족한다. {@code journal} 이 "DB 없이 도메인 테스트를
 * 돌리기 위해" 포트를 둔 것과 같은 자리이고, 여기서는 그것이 <b>"키 없이 서비스 테스트를
 * 돌린다"</b> 가 된다.
 *
 * <p><b>수량 0 인 것은 돌려주지 않는다.</b> 거래소는 닫힌 종목도 {@code positionAmt: "0"} 으로
 * 돌려주는데, 그것을 포지션으로 담으면 "포지션 있음" 이 되어 대조가 통째로 뒤집힌다. 거르는
 * 일이 구현체마다 갈리지 않도록 포트의 계약으로 못 박는다.
 */
public interface LoadExchangePositionsPort {

    /**
     * 이 종목에 열려 있는 포지션. 없으면 빈 목록이다.
     *
     * @throws com.coinwin.common.domain.ExternalDataUnavailableException 거래소를 읽지 못했을 때
     */
    List<ExchangePosition> positionsFor(Symbol symbol);
}
