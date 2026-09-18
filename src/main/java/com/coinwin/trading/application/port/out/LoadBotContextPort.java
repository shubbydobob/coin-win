package com.coinwin.trading.application.port.out;

import com.coinwin.market.domain.Symbol;
import com.coinwin.trading.domain.BotContext;

/**
 * 한 사이클이 보는 세상을 <b>한 번에</b> 읽는다.
 *
 * <p>시장과 계좌를 두 포트로 나누지 않은 이유가 이 인터페이스의 전부다. 나누면 서비스가 둘을
 * 차례로 부르고, 그 사이에 가격이 움직인다 — 같은 사이클 안에서 "지금" 이 둘이 되고 판단이
 * 재현되지 않는다. <b>포트를 좁게 두라는 원칙보다 한 순간을 지키는 쪽이 이긴다.</b>
 *
 * <p>구현체가 둘이다 — 실제 모듈에서 조립하는 것과 테스트용 고정값.
 */
public interface LoadBotContextPort {

    /**
     * 지금 이 순간의 시장·계좌·포지션.
     *
     * @throws com.coinwin.common.domain.ExternalDataUnavailableException 못 읽었을 때.
     *     <b>비어 있는 값으로 대신하지 않는다</b> — "포지션이 없다" 는 거짓말 위에서 봇이
     *     새 포지션을 연다
     */
    BotContext contextFor(Symbol symbol);
}
