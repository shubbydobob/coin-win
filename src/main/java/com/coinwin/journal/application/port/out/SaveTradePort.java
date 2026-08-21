package com.coinwin.journal.application.port.out;

import com.coinwin.journal.domain.Trade;

/**
 * 거래를 저장한다. 계획·체결·청산 <b>세 상태를 모두 같은 메서드가</b> 받는다.
 *
 * <p>상태마다 메서드를 나누지 않는 이유는 저장이 언제나 <b>같은 거래를 덮어쓰는 일</b>이기
 * 때문이다. 식별자는 계획 시점에 정해지고 이후 바뀌지 않으므로, 체결도 청산도 새 행이
 * 아니라 같은 행의 다음 상태다. {@code recordFill} / {@code recordClose} 로 나누면 저장소가
 * 도메인의 전이 규칙을 한 벌 더 알게 된다 — 그것은 {@code Trade} 가 이미 강제하고 있다.
 */
public interface SaveTradePort {

    /** 저장하거나 덮어쓴다. 같은 식별자로 두 행이 생기지 않는다. */
    void save(Trade trade);
}
