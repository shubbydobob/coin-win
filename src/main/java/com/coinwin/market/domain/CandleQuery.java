package com.coinwin.market.domain;

import com.coinwin.common.domain.DomainValues;

/**
 * 캔들 조회 조건. 세 어댑터가 공유하는 유일한 입력 형태다.
 *
 * <p>포트 메서드가 {@code (Symbol, CandleInterval, Instant, Instant)} 네 인자를 받는 대신 이
 * 객체를 받는 이유는, 인자 순서를 어댑터마다 다시 맞추다 보면 {@code from} 과 {@code to} 가
 * 뒤바뀐 호출이 컴파일을 통과하기 때문이다.
 */
public record CandleQuery(Symbol symbol, CandleInterval interval, TimeRange range) {

    public CandleQuery {
        DomainValues.required(symbol, "종목");
        DomainValues.required(interval, "캔들 주기");
        DomainValues.required(range, "조회 구간");
    }
}
