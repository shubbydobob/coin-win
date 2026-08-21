package com.coinwin.journal.application;

import com.coinwin.journal.domain.TradeId;

/**
 * 거래가 닫혔다는 사실. <b>저장이 끝난 뒤</b> 발행된다.
 *
 * <p>이 이벤트가 있는 이유는 하나다 — {@code journal} 이 {@code ai} 를 모르게 하기 위해서.
 * 청산 시 자동 색인이 필요한데 서비스가 색인 유스케이스를 직접 부르면
 * {@code journal → ai → journal} 순환이 되고, ArchUnit 규칙 3 이 빌드를 세운다.
 * 방향을 뒤집으면 듣는 쪽만 발행하는 쪽을 알면 된다.
 *
 * <p>듣는 쪽이 없어도 아무 일도 일어나지 않는다. 그것이 정상이다 — 색인은 파생이고,
 * 진실의 원천은 언제나 매매 기록이다.
 */
public record TradeClosedEvent(TradeId id) {
}
