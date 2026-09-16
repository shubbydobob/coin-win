package com.coinwin.account.domain;

/**
 * 포지션 수량 중 얼마나가 손절로 덮여 있는가.
 *
 * <p><b>{@code boolean} 하나로 줄이지 않는다.</b> 물타기로 수량이 늘었는데 손절은 옛 수량
 * 그대로인 상태 — {@link #PARTIAL} — 가 "손절 있음" 으로 읽히면, 그것이 바로
 * {@code docs/spec/exit-automation.md} § 0 의 −2,000 이 자라던 모양이다. 셋으로 갈라야
 * 화면이 "절반만 덮여 있다" 를 말할 수 있다.
 *
 * <p>{@link PositionMatch} 가 네 경우를 타입으로 가른 것과 같은 판단이고, 여기서는 담고 다니는
 * 값이 경우마다 같으므로 열거형이면 충분하다.
 */
public enum StopLossCoverage {

    /** 손절이 하나도 없다. 규칙 R1 이 존재할 수 없다고 말하는 상태다. */
    NONE,

    /** 손절이 있는데 포지션 전량을 덮지 못한다. 덮이지 않은 만큼은 손절이 없는 것과 같다. */
    PARTIAL,

    /** 전량이 덮여 있다. */
    FULL;

    /** 사람이 지금 해야 할 일이 있는가. */
    public boolean needsAttention() {
        return this != FULL;
    }
}
