package com.coinwin.backtest.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Money;
import java.math.BigDecimal;

/**
 * 대를 만드는 설정. 전부 봉 수이거나 ATR 배수라 <b>타임프레임에 종속되지 않는다.</b>
 *
 * @param pivotLookback 스윙 극값 판정에 쓸 좌우 봉 수. 확정 지연도 이 값이다
 * @param clusterMultiple 같은 대로 묶을 최대 간격. 단위는 ATR 배수
 * @param minTouches 대로 채택할 최소 터치 수
 * @param atrPeriod ATR 평활 구간
 */
public record ZoneSettings(
        int pivotLookback, BigDecimal clusterMultiple, int minTouches, int atrPeriod) {

    public ZoneSettings {
        DomainValues.atLeast(pivotLookback, 1, "피벗 탐지 폭");
        DomainValues.required(clusterMultiple, "군집 배수");
        DomainValues.atLeast(minTouches, 2, "최소 터치 횟수");
        DomainValues.atLeast(atrPeriod, 1, "ATR 기간");
        if (clusterMultiple.signum() < 0) {
            throw new InvalidBacktestException(
                    "군집 배수는 음수일 수 없다: " + clusterMultiple.toPlainString());
        }
    }

    /** 출발점이지 결론이 아니다. 실제 캔들로 돌려 보고 정한다. */
    public static ZoneSettings standard() {
        return new ZoneSettings(5, new BigDecimal("0.5"), 2, 14);
    }

    /** 이 시점의 변동성에서 같은 대로 볼 최대 간격. */
    public Money toleranceFor(Money atr) {
        DomainValues.required(atr, "ATR");
        return atr.times(clusterMultiple);
    }
}
