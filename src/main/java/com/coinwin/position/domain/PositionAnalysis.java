package com.coinwin.position.domain;

import com.coinwin.common.domain.Money;
import java.math.BigDecimal;
import java.util.List;

/**
 * 계획 하나에 대한 산출 결과 전체. 체결 상태별로 한 건씩 담는다.
 *
 * <p>{@code fillStates} 는 1건 체결부터 전량 체결까지 순서대로다. 마지막 원소가 전량 체결이다.
 *
 * <p>{@code weakRiskReward} 는 여기서 다시 계산하지 않고 계획에서 받아 온다. {@code
 * riskRewardRatio} 는 표시용으로 자릿수를 줄인 값이라, 그것으로 기준을 판정하면 경계에서
 * 결과가 갈린다. 판정에 필요한 정밀도를 갖고 있지 않은 쪽이 판정해서는 안 된다.
 */
public record PositionAnalysis(
        List<FillState> fillStates,
        Money requiredMargin,
        Money accountBalance,
        BigDecimal riskRewardRatio,
        boolean weakRiskReward) {

    public PositionAnalysis {
        PositionValues.required(fillStates, "체결 상태");
        if (fillStates.isEmpty()) {
            throw new InvalidPositionPlanException("체결 상태가 하나도 없는 산출 결과는 성립하지 않는다");
        }
        fillStates = List.copyOf(fillStates);
    }

    /** 1차만 체결된 상태. "아직 여유 있다" 는 착각이 생기는 지점이다. */
    public FillState afterFirstEntry() {
        return fillStates.getFirst();
    }

    /** 전량 체결 상태. 1차 진입 시점에 이미 확정되어 있는 최종 리스크다. */
    public FillState whenFullyFilled() {
        return fillStates.getLast();
    }

    /** 필요 증거금이 잔고를 넘으면 이 계획은 애초에 열 수 없다. 경고로만 알린다. */
    public boolean marginExceedsBalance() {
        return requiredMargin.isGreaterThan(accountBalance);
    }
}
