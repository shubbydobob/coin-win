package com.coinwin.common.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * USDT 하나가 몇 원인가. 환산은 이 객체만 한다.
 *
 * <p><b>잰 시각을 함께 갖는다.</b> 환율은 매 순간 달라지므로 "1,370원" 만으로는 그것이 지금
 * 값인지 어제 값인지 알 수 없고, 화면이 그 구분 없이 원화를 띄우면 사람은 언제나 지금
 * 값으로 읽는다. {@code Ticker} 가 같은 이유로 시각을 갖는다.
 *
 * <p>{@code common} 에 두는 이유는 {@link Money} 와 {@link Won} 사이의 <b>단위 변환</b>이기
 * 때문이다. 어느 거래소에서 얻었는가는 어댑터의 사정이고, 이 타입은 그것을 모른다 —
 * 그래서 {@code market} 이 아니라 값 객체 옆에 있다.
 */
public record ExchangeRate(BigDecimal wonPerUsdt, Instant observedAt) {

    /** 원화 호가는 소수 둘째 자리까지 온다. 환율의 스케일은 원화 금액의 스케일과 다르다. */
    private static final int SCALE = 2;

    private static final String LABEL = "환율";

    public ExchangeRate {
        DomainValues.required(observedAt, "환율을 잰 시각");
        wonPerUsdt = DecimalValues.normalizeNonNegative(wonPerUsdt, SCALE, LABEL);
        if (wonPerUsdt.signum() <= 0) {
            throw new InvalidValueException(
                    "환율은 0 보다 커야 한다: " + wonPerUsdt.toPlainString());
        }
    }

    /** USDT 금액을 원화로 옮긴다. 반올림 정책은 {@link Won} 이 갖는다. */
    public Won convert(Money usdt) {
        return Won.of(DomainValues.required(usdt, "금액").value().multiply(wonPerUsdt));
    }
}
