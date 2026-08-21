package com.coinwin.backtest.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Price;
import java.time.Instant;

/**
 * 스윙 극값 하나. 대(帶)를 만드는 재료다.
 *
 * <p><b>{@code confirmedAt} 이 이 타입의 존재 이유다.</b> index {@code i} 가 피벗인지 알려면
 * {@code i + lookback} 봉까지 봐야 하므로, 피벗이 발생한 시각과 그것을 알 수 있게 된 시각이
 * 다르다. 두 시각을 한 필드로 뭉개면 백테스트가 미래를 보게 된다 — 시점 {@code t} 에서
 * {@code at ≤ t} 인 피벗을 고르면 아직 확정되지 않은 극값이 섞여 들어온다.
 *
 * @param at 극값이 실제로 찍힌 캔들의 시각
 * @param confirmedAt 그것이 피벗임을 알 수 있게 된 캔들의 시각. 항상 {@code at} 이후
 * @param price 극값. 고점이면 고가, 저점이면 저가
 * @param kind 고점인가 저점인가
 */
public record Pivot(Instant at, Instant confirmedAt, Price price, PivotKind kind) {

    public Pivot {
        DomainValues.required(at, "피벗 시각");
        DomainValues.required(confirmedAt, "피벗 확정 시각");
        DomainValues.required(price, "피벗 가격");
        DomainValues.required(kind, "피벗 종류");
        if (confirmedAt.isBefore(at)) {
            throw new InvalidBacktestException(
                    "피벗 확정 시각은 발생 시각보다 앞설 수 없다: %s 발생, %s 확정"
                            .formatted(at, confirmedAt));
        }
    }

    /** 시점 {@code moment} 에 이 피벗을 알 수 있는가. 확정 시각의 캔들이 닫혀야 한다. */
    public boolean isKnownAt(Instant moment) {
        DomainValues.required(moment, "기준 시각");
        return !confirmedAt.isAfter(moment);
    }
}
