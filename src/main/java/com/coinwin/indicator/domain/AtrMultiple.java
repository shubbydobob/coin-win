package com.coinwin.indicator.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Price;
import java.math.BigDecimal;
import java.math.MathContext;

/**
 * 거리를 <b>그 시점의 변동성으로 잰</b> 배수.
 *
 * <p><b>왜 이 단위인가.</b> 가격 차를 USDT 로 두면 60,000 시절과 100,000 시절이 비교되지 않고,
 * % 로 두면 변동성이 죽은 구간과 살아 있는 구간이 비교되지 않는다. "전환선이 기준선보다
 * 300 위" 는 조용한 장에서는 큰 값이고 급한 장에서는 아무것도 아니다. 대의 폭과 손절 버퍼가
 * 이미 이 단위로 정해져 있다({@code ZoneSettings}).
 *
 * <p><b>부호를 남긴다.</b> 위인지 아래인지가 이 값이 말하는 것의 절반이므로 절대값으로
 * 접지 않는다. {@link Money} 와 같은 태도다.
 *
 * <p><b>스케일 2 인 이유는 읽는 사람 때문이다.</b> 1.5배와 1.53배를 가르는 판단은 없고,
 * 자릿수가 늘면 정밀해 보이는 만큼 근거가 있는 것처럼 읽힌다.
 */
public record AtrMultiple(BigDecimal value) {

    private static final int SCALE = 2;

    private static final String LABEL = "ATR 배수";

    public AtrMultiple {
        value = DomainValues.scaled(value, SCALE, LABEL);
    }

    /**
     * 거리를 ATR 로 잰다.
     *
     * @throws InvalidIndicatorException ATR 이 0 이하인 경우. <b>배수를 말할 수 없다</b> —
     *     0 으로 적으면 "가깝다" 는 없는 사실이 생기고, 큰 수로 적으면 그 반대가 생긴다
     */
    public static AtrMultiple of(Money distance, Money atr) {
        DomainValues.required(distance, "거리");
        DomainValues.required(atr, "ATR");
        if (atr.value().signum() <= 0) {
            throw new InvalidIndicatorException(
                    "ATR 이 0 이하면 배수를 말할 수 없다: " + atr.value().toPlainString());
        }
        return new AtrMultiple(distance.value().divide(atr.value(), MathContext.DECIMAL64));
    }

    /** 두 가격 사이의 거리. <b>뺀 순서가 부호를 정한다</b> — 앞이 위면 양수다. */
    public static AtrMultiple between(Price from, Price to, Money atr) {
        DomainValues.required(from, "기준 가격");
        DomainValues.required(to, "견줄 가격");
        return of(from.asAmount().minus(to.asAmount()), atr);
    }
}
