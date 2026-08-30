package com.coinwin.readout.domain;

import com.coinwin.common.domain.DomainValues;

/**
 * 지표 하나가 선 자리와 그 근거 문장.
 *
 * <p><b>문장이 함께 있는 것이 요점이다.</b> "위" 만 있으면 무엇을 보고 그렇게 말했는지가
 * 사라지고, 그 순간 이 값은 관측이 아니라 판정으로 읽힌다. 「붐비는 쪽」이 지표마다 다른
 * 문장을 다는 것과 같은 이유다 — 같은 "위" 여도 근거가 전부 다르다.
 *
 * <p><b>무리는 따로 받지 않는다.</b> 지표가 정해지면 무리도 정해지므로 받으면
 * <b>지표와 무리가 어긋난 묶음</b>을 만들 수 있다.
 *
 * @param indicator 어느 지표인가. 이름과 무리를 함께 갖는다
 * @param stance 어느 쪽에 서 있는가
 * @param statement <b>일어난 일까지만</b> 적은 문장. 무엇을 하라고 말하지 않는다
 */
public record IndicatorStance(IndicatorKind indicator, Stance stance, String statement) {

    public IndicatorStance {
        DomainValues.required(indicator, "지표");
        DomainValues.required(stance, "선 자리");
        DomainValues.required(statement, "문장");
    }

    /** 합치면 안 되는 두 무리 중 어느 쪽인가. 근거는 {@link IndicatorFamily} 다. */
    public IndicatorFamily family() {
        return indicator.family();
    }

    static IndicatorStance unknown(IndicatorKind indicator) {
        return new IndicatorStance(indicator, Stance.UNKNOWN, "봉이 모자라 말할 수 없다");
    }
}
