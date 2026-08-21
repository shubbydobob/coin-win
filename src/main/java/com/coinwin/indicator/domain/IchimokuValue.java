package com.coinwin.indicator.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Price;
import java.util.Optional;

/**
 * 한 시점의 일목균형표 다섯 선.
 *
 * <p><b>선행스팬 둘은 이미 변위가 적용된 값이다</b> — 이 시점에서 실제로 유효한 구름이지,
 * 이 시점에서 계산된 값이 아니다. 그래서 {@link #positionOf} 에 지금 가격을 그대로 넣으면 된다.
 * 원시값을 담고 소비자가 26봉을 세게 하면 그 산술이 호출부마다 어긋난다.
 *
 * <p><b>후행스팬만 {@link Optional} 이다.</b> 후행스팬은 26봉 뒤의 종가를 끌어오므로 최근
 * 26봉에서는 아직 존재하지 않는다. 이것을 필수 필드로 두면 다섯 선이 모두 확정된 구간만 값을
 * 낼 수 있게 되고, 그러면 <b>가장 최근 봉의 구름 위치</b>—실사용에서 가장 자주 보는 값—가
 * 사라진다. 후행스팬 하나가 없다고 나머지 넷을 버릴 이유는 없다.
 */
public record IchimokuValue(
        Price conversionLine,
        Price baseLine,
        Price leadingSpanA,
        Price leadingSpanB,
        Optional<Price> laggingSpan) {

    public IchimokuValue {
        DomainValues.required(conversionLine, "전환선");
        DomainValues.required(baseLine, "기준선");
        DomainValues.required(leadingSpanA, "선행스팬 1");
        DomainValues.required(leadingSpanB, "선행스팬 2");
        DomainValues.required(laggingSpan, "후행스팬");
    }

    /**
     * 두 선행스팬이 감싸는 구간.
     *
     * <p>어느 쪽이 위인지는 정해져 있지 않다. 선행스팬 1 이 2 아래로 내려간 구름이 하락 구름이고,
     * 뒤집히는 것 자체가 추세 전환 신호다. 그래서 {@link PriceBand#enclosing} 으로 만든다.
     */
    public PriceBand cloud() {
        return PriceBand.enclosing(leadingSpanA, leadingSpanB);
    }

    public BandPosition positionOf(Price price) {
        return cloud().positionOf(price);
    }

    /** 선행스팬 1 이 2 위에 있으면 상승 구름이다. */
    public boolean bullishCloud() {
        return leadingSpanA.isAbove(leadingSpanB);
    }
}
