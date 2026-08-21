package com.coinwin.market.domain;

import java.time.Instant;

/**
 * 한 묶음에 같은 {@code openTime} 이 두 번 나타날 때 던진다.
 *
 * <p>일반적인 시장 데이터 오류와 타입을 나눈 이유는, 이것이 Phase 3 완료 조건 그 자체이기
 * 때문이다 — "캔들 증분 저장에 중복 없음". 어느 어댑터에서 중복이 새어 나왔는지 추적할 때
 * 예외 타입이 곧 신호가 된다.
 */
public class DuplicateCandleException extends InvalidMarketDataException {

    private static final long serialVersionUID = 1L;

    public DuplicateCandleException(Instant openTime) {
        super("같은 시각의 캔들이 두 번 들어왔다: " + openTime);
    }
}
