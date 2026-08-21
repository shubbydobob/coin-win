package com.coinwin.journal.domain;

/**
 * 포지션이 닫힌 이유. <b>계획 준수 여부의 판정을 이 enum 이 소유한다.</b>
 *
 * <p>판정을 집계 쪽에 두면 "어떤 이유가 계획 준수인가" 가 호출부마다 흩어지고, 새 이유를
 * 추가할 때 한 곳만 고쳐서는 끝나지 않는다. 규칙이 값과 함께 있어야 한다.
 *
 * <p>구분의 근거는 {@code .claude/docs/scope.md} 가 든 두 번째 문제다 — 규칙을 지키고 진
 * 거래와 규칙을 어기고 이긴 거래는 다른 데이터이므로 손익을 같은 칸에 담으면 안 된다.
 */
public enum ExitReason {

    /** 계획한 손절가에서 닫혔다. 손실이지만 계획대로다. */
    PLANNED_STOP(true),

    /** 계획한 익절가에서 닫혔다. */
    PLANNED_TARGET(true),

    /** 손절·익절 어느 쪽에도 닿기 전에 사람이 먼저 닫았다. */
    MANUAL_EARLY(false),

    /** 손절가를 지나쳤는데도 들고 있었다. 이 모듈이 가장 잡고 싶어 하는 경우다. */
    HELD_PAST_STOP(false),

    /** 거래소가 강제로 닫았다. 손절이 작동하지 않았다는 뜻이다. */
    LIQUIDATED(false);

    private final boolean honorsPlan;

    ExitReason(boolean honorsPlan) {
        this.honorsPlan = honorsPlan;
    }

    public boolean honorsPlan() {
        return honorsPlan;
    }
}
