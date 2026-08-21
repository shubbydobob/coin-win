package com.coinwin.market.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.InvalidValueException;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Percentage;

/**
 * 명목가 구간 하나에 대한 유지증거금 규칙.
 *
 * <p>유지증거금은 {@code 명목가 × maintenanceMarginRate - maintenanceAmount} 다.
 * 공제액이 있는 이유는 구간이 바뀌는 지점에서 유지증거금이 계단처럼 뛰지 않게 하기 위해서다.
 * 그 연속 조건은 {@link LeverageBrackets} 가 표 전체에 대해 검사한다.
 */
public record LeverageBracket(
        int tier,
        Money notionalCap,
        Percentage maintenanceMarginRate,
        Money maintenanceAmount) {

    private static final Money ZERO = Money.of("0");

    public LeverageBracket {
        DomainValues.atLeast(tier, 1, "구간 번호");
        DomainValues.required(notionalCap, "구간 상한");
        DomainValues.required(maintenanceMarginRate, "유지증거금률");
        DomainValues.required(maintenanceAmount, "유지증거금 공제액");
        if (!notionalCap.isGreaterThan(ZERO)) {
            throw new InvalidValueException("구간 상한은 0보다 커야 한다: " + notionalCap.value());
        }
    }

    /** 상한은 <b>포함</b>이다. 명목가가 정확히 상한이면 아직 이 구간이다. */
    public boolean covers(Money notional) {
        return !notional.isGreaterThan(notionalCap);
    }
}
