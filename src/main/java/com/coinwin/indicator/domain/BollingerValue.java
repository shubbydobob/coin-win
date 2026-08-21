package com.coinwin.indicator.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Percentage;
import com.coinwin.common.domain.Price;

/**
 * 한 시점의 볼린저 밴드 — 중심선과 그 위아래로 벌어진 두 경계.
 *
 * @param upper 상단. 중심선 + 표준편차 × 배수
 * @param middle 중심선. 종가 이동평균
 * @param lower 하단. 중심선 − 표준편차 × 배수
 */
public record BollingerValue(Price upper, Price middle, Price lower) {

    public BollingerValue {
        DomainValues.required(upper, "밴드 상단");
        DomainValues.required(middle, "밴드 중심선");
        DomainValues.required(lower, "밴드 하단");
        assertMiddleIsEnclosed(upper, middle, lower);
    }

    public PriceBand band() {
        return new PriceBand(upper, lower);
    }

    public BandPosition positionOf(Price price) {
        return band().positionOf(price);
    }

    /**
     * 중심선 대비 밴드의 폭. 변동성 수축·확장을 가격대와 무관하게 비교하기 위한 값이다.
     *
     * <p>절대 폭(USDT)으로는 60,000 짜리 자산과 3,000 짜리 자산의 수축을 비교할 수 없다.
     */
    public Percentage bandWidth() {
        return band().width().percentOf(middle.asAmount());
    }

    /**
     * 중심선은 정의상 두 경계 사이에 있다.
     *
     * <p>벗어났다면 셋 중 하나가 다른 구간에서 계산된 것이고, 그 순간 밴드폭과 위치 판정이
     * <b>동시에</b> 틀어진다. 여기서 막지 않으면 두 증상이 따로 나타나 원인을 찾기 어렵다.
     */
    private static void assertMiddleIsEnclosed(Price upper, Price middle, Price lower) {
        if (new PriceBand(upper, lower).positionOf(middle) != BandPosition.INSIDE) {
            throw new InvalidIndicatorException(
                    "밴드 중심선은 상단과 하단 사이에 있어야 한다: 상단 %s, 중심선 %s, 하단 %s"
                            .formatted(upper.value(), middle.value(), lower.value()));
        }
    }
}
