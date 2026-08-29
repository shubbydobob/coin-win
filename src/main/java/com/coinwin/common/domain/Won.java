package com.coinwin.common.domain;

import java.math.BigDecimal;

/**
 * 원화 금액. 스케일 0, HALF_UP.
 *
 * <p><b>소수점이 없다.</b> 원은 그 아래 단위가 없고, 1,982,632.20원 은 존재하지 않는 금액이다.
 * {@link Money} 를 그대로 쓰면 USDT 의 스케일 2 가 원화에 그대로 실린다.
 *
 * <p>{@link Money} 처럼 음수를 허용한다. 환산 대상이 손실일 수 있기 때문이다.
 *
 * <p><b>이 값 객체는 계산에 쓰지 않는다.</b> 매매 계산은 전부 USDT 에서 끝나고, 원화는
 * 그 결과를 사람이 감으로 읽기 위한 <b>표시 단위</b>다. 그래서 사칙연산 메서드가 없다 —
 * 원화끼리 더하거나 빼야 할 일이 생겼다면 그 계산은 USDT 쪽에서 끝냈어야 한다.
 */
public record Won(BigDecimal value) {

    private static final int SCALE = 0;
    private static final String LABEL = "원화 금액";

    public Won {
        value = DecimalValues.normalize(value, SCALE, LABEL);
    }

    public static Won of(BigDecimal value) {
        return new Won(value);
    }
}
