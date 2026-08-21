package com.coinwin.market.application.port.out;

import com.coinwin.market.domain.LeverageBrackets;
import com.coinwin.market.domain.Symbol;

/**
 * 레버리지 구간표를 읽어 오는 곳. 구현체는 둘이다 — 거래소와 번들 스냅샷.
 *
 * <p>스냅샷 구현체가 있는 이유는 포지션 분석이 <b>네트워크 없이도 동작해야</b> 하기
 * 때문이다. 청산가는 이 표에 의존하는데, 거래소가 닿지 않는다고 계획 검토가 멈추면
 * 도구로서 쓸모가 없다. 어느 쪽을 쓸지는 {@code market.application} 이 정한다.
 */
public interface LoadLeverageBracketsPort {

    LeverageBrackets bracketsFor(Symbol symbol);
}
