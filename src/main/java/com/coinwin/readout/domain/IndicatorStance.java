package com.coinwin.readout.domain;

import com.coinwin.common.domain.DomainValues;

/**
 * 지표 하나가 선 자리와 그 근거 문장.
 *
 * <p><b>문장이 함께 있는 것이 요점이다.</b> "롱" 만 있으면 무엇을 보고 그렇게 말했는지가
 * 사라지고, 그 순간 이 값은 관측이 아니라 판정으로 읽힌다. 「붐비는 쪽」이 지표마다 다른
 * 문장을 다는 것과 같은 이유다 — 같은 "롱 쪽" 이어도 근거가 전부 다르다.
 *
 * @param indicator 지표 이름
 * @param stance 어느 쪽에 서 있는가
 * @param statement <b>일어난 일까지만</b> 적은 문장. 무엇을 하라고 말하지 않는다
 */
public record IndicatorStance(String indicator, Stance stance, String statement) {

    public IndicatorStance {
        DomainValues.required(indicator, "지표 이름");
        DomainValues.required(stance, "선 자리");
        DomainValues.required(statement, "문장");
    }

    static IndicatorStance unknown(String indicator) {
        return new IndicatorStance(indicator, Stance.UNKNOWN, "봉이 모자라 말할 수 없다");
    }
}
