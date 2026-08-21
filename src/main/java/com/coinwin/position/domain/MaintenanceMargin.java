package com.coinwin.position.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Percentage;

/**
 * 한 포지션 크기에 적용되는 유지증거금 규칙. {@code 유지증거금 = 명목가 × rate - deduction}.
 *
 * <p>{@code rate} 하나가 아니라 {@code deduction} 과 쌍인 이유는, 거래소가 구간마다 다른
 * 비율을 쓰면서도 구간 경계에서 유지증거금이 끊기지 않게 하기 때문이다. 공제액이 그 이음매다.
 * 이것을 빼면 명목가가 1 USDT 늘었을 때 유지증거금이 계단처럼 뛴다.
 *
 * <p>{@code position/domain} 에 두는 이유는 이것이 <b>청산가 공식의 입력</b>이기 때문이다.
 * 값이 어디서 오는지({@code market} 의 구간표든 고정 근사치든)는 이 계층의 관심이 아니다.
 */
public record MaintenanceMargin(Percentage rate, Money deduction) {

    private static final Money NO_DEDUCTION = Money.of("0");

    public MaintenanceMargin {
        DomainValues.required(rate, "유지증거금률");
        DomainValues.required(deduction, "유지증거금 공제액");
    }

    /** 구간이 하나뿐이라 이음매가 필요 없는 경우. 공제액은 0 이다. */
    public static MaintenanceMargin flatRate(Percentage rate) {
        return new MaintenanceMargin(rate, NO_DEDUCTION);
    }
}
