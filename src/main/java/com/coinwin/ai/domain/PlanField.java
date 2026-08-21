package com.coinwin.ai.domain;

/**
 * 계획 초안이 가져야 하는 칸. 빠졌을 때 사용자에게 무엇을 더 말해 달라고 할지가 이 목록이다.
 *
 * <p><b>총수량이 없다.</b> 이 프로젝트에서 수량은 입력이 아니라 손절가가 결정하는 결과이기
 * 때문이다({@code PositionPlan.totalQuantity}). 초안에 수량 칸을 두면 모델이 채운 숫자가
 * 리스크 사이징을 건너뛰고 계획으로 들어간다.
 */
public enum PlanField {

    DIRECTION("방향"),
    ENTRIES("분할 진입 계획"),
    STOP_LOSS("손절가"),
    TAKE_PROFIT("익절가"),
    LEVERAGE("레버리지");

    private final String label;

    PlanField(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
