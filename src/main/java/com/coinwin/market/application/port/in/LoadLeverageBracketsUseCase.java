package com.coinwin.market.application.port.in;

import com.coinwin.market.domain.LeverageBrackets;
import com.coinwin.market.domain.Symbol;

/**
 * 레버리지 구간표. {@code position} 이 청산가를 구할 때 이 포트를 통해 받는다.
 *
 * <p>인바운드 포트인 이유는 {@code position} 이 {@code market} 을 <b>구동하는</b> 쪽이기
 * 때문이다. 아웃바운드 포트를 직접 쓰게 하면 "거래소에서 받되 실패하면 스냅샷" 이라는 정책이
 * {@code position} 으로 새어 나간다. 그 정책은 {@code market} 의 것이다.
 */
public interface LoadLeverageBracketsUseCase {

    LeverageBrackets bracketsFor(Symbol symbol);
}
