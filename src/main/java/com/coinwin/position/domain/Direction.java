package com.coinwin.position.domain;

/**
 * 포지션 방향.
 *
 * <p>손절가·익절가가 놓여야 하는 쪽과 청산가 공식의 부호가 여기서 갈린다.
 * 그 외의 계산은 방향과 무관하다.
 */
public enum Direction {

    /** 매수. 가격이 오르면 이익. 청산가는 진입가 아래에 있다. */
    LONG,

    /** 매도. 가격이 내리면 이익. 청산가는 진입가 위에 있다. */
    SHORT
}
