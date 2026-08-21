package com.coinwin.projection.domain;

/**
 * 거래 한 건의 결과. 익절 아니면 손절이고 그 사이는 없다.
 *
 * <p>본전 청산이나 부분 익절을 넣지 않은 이유는 이 모듈의 입력이 <b>승률과 손익비 두 숫자</b>
 * 뿐이기 때문이다. 그 두 숫자로 표현되지 않는 결과를 모델에 넣으면 입력에 없는 정보를
 * 지어내는 셈이 된다. 실제 체결 분포를 다루는 것은 Phase 6 백테스트의 몫이다.
 */
public enum TradeOutcome {
    WIN,
    LOSS
}
