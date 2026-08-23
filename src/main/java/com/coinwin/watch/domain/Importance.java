package com.coinwin.watch.domain;

/**
 * 이 일정이 사전 경고를 받을 만한가.
 *
 * <p>둘뿐인 이유는 <b>셋이 되는 순간 "중간" 이 아무 뜻도 갖지 못하기 때문</b>이다. 경고를
 * 띄울 것인가 말 것인가가 이 값이 답하는 유일한 질문이다.
 */
public enum Importance {

    /** 다가오면 경고한다. */
    HIGH,

    /** 목록에는 있지만 경고하지 않는다. */
    NORMAL
}
