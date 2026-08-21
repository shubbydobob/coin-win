package com.coinwin.indicator.domain;

import com.coinwin.common.domain.DomainValues;
import java.time.Instant;

/**
 * 어느 시각의 지표 값인가.
 *
 * <p>지표 계산 결과를 값만 담은 목록으로 돌려주면 <b>몇 번째 캔들의 값인지가 인덱스 산술로만</b>
 * 남는다. 워밍업 구간만큼 앞이 잘려 있으므로 그 산술은 호출부마다 어긋나기 쉽고, 어긋나도
 * 조용히 틀린 값이 나간다. 시각을 값에 붙여 두면 캔들과 맞대 볼 수 있다.
 *
 * <p>시각은 해당 캔들의 {@code openTime} 이다 — 즉 <b>그 캔들이 닫힌 뒤 유효한 값</b>이다.
 *
 * @param <T> 지표 값 타입 ({@link IchimokuValue} / {@link BollingerValue})
 */
public record IndicatorPoint<T>(Instant at, T value) {

    public IndicatorPoint {
        DomainValues.required(at, "지표 시각");
        DomainValues.required(value, "지표 값");
    }
}
