package com.coinwin.journal.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Price;
import java.time.Instant;

/**
 * 포지션을 닫은 체결. 진입과 달리 <b>수량이 없다</b> — 전량 청산만 기록한다.
 *
 * <p>부분 청산을 담지 않는 것은 YAGNI 다. {@code .claude/docs/scope.md} 의 매매 전제는
 * 분할 <b>진입</b> 이고, 청산은 손절가 또는 익절가 한 지점이다. 부분 청산이 실제로 생기면
 * 그때 {@code Fill} 목록으로 바꾼다 — 지금 넣으면 검증할 테스트가 없는 코드가 된다.
 */
public record Exit(Price price, Instant at) {

    public Exit {
        DomainValues.required(price, "청산가");
        DomainValues.required(at, "청산 시각");
    }
}
