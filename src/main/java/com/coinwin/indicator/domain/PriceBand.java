package com.coinwin.indicator.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Price;

/**
 * 상단과 하단으로 둘러싸인 가격 구간. 일목 구름과 볼린저 밴드의 공통 형태다.
 *
 * <p><b>경계는 구간에 포함된다.</b> 돌파는 "경계를 넘었는가" 이지 "경계에 닿았는가" 가 아니다.
 * 닿은 것을 돌파로 치면 종가가 상단에 정확히 걸린 캔들마다 신호가 생긴다.
 */
public record PriceBand(Price upper, Price lower) {

    public PriceBand {
        DomainValues.required(upper, "밴드 상단");
        DomainValues.required(lower, "밴드 하단");
        if (upper.isBelow(lower)) {
            throw new InvalidIndicatorException(
                    "밴드 상단은 하단보다 낮을 수 없다: 상단 %s, 하단 %s"
                            .formatted(upper.value(), lower.value()));
        }
    }

    /**
     * 두 가격을 감싸는 밴드. 어느 쪽이 위인지 모를 때 쓴다.
     *
     * <p>일목 선행스팬 1·2 는 순서가 정해져 있지 않고, 뒤집히는 것 자체가 추세 전환 신호다.
     * 큰 쪽을 고르는 비교를 호출부마다 두면 그 규칙이 흩어진다.
     */
    public static PriceBand enclosing(Price one, Price other) {
        DomainValues.required(one, "밴드 경계");
        DomainValues.required(other, "밴드 경계");
        return one.isBelow(other) ? new PriceBand(other, one) : new PriceBand(one, other);
    }

    public BandPosition positionOf(Price price) {
        DomainValues.required(price, "위치를 판정할 가격");
        if (price.isAbove(upper)) {
            return BandPosition.ABOVE;
        }
        if (price.isBelow(lower)) {
            return BandPosition.BELOW;
        }
        return BandPosition.INSIDE;
    }

    /** 상단과 하단의 간격. 1단위당 금액이므로 {@link Money} 다. */
    public Money width() {
        return upper.absoluteDifference(lower);
    }
}
