package com.coinwin.market.application.port.out;

import com.coinwin.market.domain.CandleQuery;
import com.coinwin.market.domain.CandleSeries;

/**
 * 캔들을 읽어 오는 곳. 이 프로젝트에서 구현체가 셋인 유일한 포트다.
 *
 * <p>실시간 거래소 / 저장된 과거 데이터 / 테스트용 인메모리. 이 셋이 존재하기 때문에
 * {@code market} 을 포트·어댑터로 만들었다. 근거: {@code docs/adr/002}.
 *
 * <p>세 구현체는 <b>같은 계약 테스트 스위트</b>를 통과해야 한다. 그것이 이 추상화가 실제로
 * 성립하는지 확인하는 유일한 방법이다. 근거: {@code .claude/docs/testing.md}.
 *
 * <p>메서드가 하나뿐인 이유는 인터페이스 분리 원칙이다. 저장은 {@link SaveCandlesPort} 가
 * 맡는다 — 거래소 어댑터는 저장할 수 없고, 저장만 하는 호출자는 조회를 알 필요가 없다.
 */
public interface LoadCandlesPort {

    /**
     * 조회 구간에 든 캔들. 구간은 반열림 {@code [from, to)} 다.
     *
     * <p>구현체는 결과를 시간 오름차순으로, 같은 시각이 두 번 없게 돌려줘야 한다. 그 검사는
     * {@link CandleSeries} 가 대신하므로 구현체가 따로 할 일은 없다 — 대신 어기면 터진다.
     *
     * <p>데이터가 없으면 빈 묶음을 돌려준다. 예외가 아니다.
     */
    CandleSeries load(CandleQuery query);
}
