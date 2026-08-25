package com.coinwin.indicator.domain;

import com.coinwin.common.domain.DomainValues;
import java.math.BigDecimal;

/**
 * 한 시점의 MACD 세 값.
 *
 * <p><b>가격이 아니라 가격의 차다.</b> 그래서 {@code Price} 가 아니고 음수가 될 수 있다 —
 * 빠른 평균이 느린 평균보다 아래면 음수이고, 그 부호가 이 지표의 절반이다.
 *
 * @param macd 빠른 EMA − 느린 EMA
 * @param signal MACD 의 EMA
 * @param histogram MACD − 시그널
 */
public record MacdValue(BigDecimal macd, BigDecimal signal, BigDecimal histogram) {

    public MacdValue {
        DomainValues.required(macd, "MACD");
        DomainValues.required(signal, "시그널");
        DomainValues.required(histogram, "히스토그램");
    }

    /** 히스토그램은 언제나 나머지 둘의 차다. 따로 넘기지 않고 여기서 맞춘다. */
    static MacdValue of(BigDecimal macd, BigDecimal signal) {
        return new MacdValue(macd, signal, macd.subtract(signal));
    }
}
