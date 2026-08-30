package com.coinwin.indicator.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Price;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Optional;

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

    /**
     * 상단과 하단의 한가운데.
     *
     * <p>구간을 한 값으로 줄여야 할 때 쓴다. <b>줄이는 것이 기본이 되면 안 된다</b> — 밴드에
     * 폭이 있다는 것이 이 타입의 요점이고, 가운데만 보면 뚫렸는지를 말할 수 없다.
     */
    public Price middle() {
        return Price.of(upper.value().add(lower.value())
                .divide(BigDecimal.TWO, MathContext.DECIMAL64));
    }

    /** 상단과 하단의 간격. 1단위당 금액이므로 {@link Money} 다. */
    public Money width() {
        return upper.absoluteDifference(lower);
    }

    /**
     * 밴드 안에서 어디쯤인가. 하단이 0, 상단이 1 이다.
     *
     * <p><b>{@link #positionOf} 가 답하지 못하는 것을 답한다.</b> 위치는 밖인지 안인지까지만
     * 말하는데, 밴드 안에서도 하단에 붙어 있는 것과 중심선 바로 아래인 것은 전혀 다른 자리다.
     *
     * <p><b>폭이 0 이면 비어 있다.</b> 상단과 하단이 같은 값이면 "어디쯤" 이라는 물음 자체가
     * 성립하지 않는다 — 0 이나 0.5 로 적으면 그것이 없는 사실이 된다. 폭이 0 인 밴드는
     * 변동성이 완전히 죽은 구간(볼린저)이거나 두 선행스팬이 겹친 자리(일목)이고, 둘 다
     * 나머지 판정은 그대로 성립하므로 <b>이 값 하나만 빠진다.</b>
     */
    public Optional<BandRatio> ratioOf(Price price) {
        DomainValues.required(price, "위치를 판정할 가격");
        BigDecimal span = width().value();
        if (span.signum() == 0) {
            return Optional.empty();
        }
        BigDecimal above = price.asAmount().minus(lower.asAmount()).value();
        return Optional.of(new BandRatio(above.divide(span, MathContext.DECIMAL64)));
    }
}
