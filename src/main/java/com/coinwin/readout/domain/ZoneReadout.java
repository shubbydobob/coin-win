package com.coinwin.readout.domain;

import com.coinwin.backtest.domain.PriceZone;
import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Percentage;
import com.coinwin.common.domain.Price;
import java.math.BigDecimal;
import java.math.MathContext;

/**
 * 지금 가격에서 가장 가까운 대 하나.
 *
 * <p><b>대의 정의를 새로 쓰지 않는다.</b> {@link PriceZone} 을 그대로 받아 옮긴다 — 화면이
 * 보여 주는 지지·저항이 백테스트가 7년으로 검증한 그것과 같아야 하기 때문이다. 같은 뜻의
 * 타입을 여기 또 만들면 한쪽 규칙만 바뀌는 순간 <b>검증한 것과 보고 있는 것이 달라지고,
 * 그러면 그 검증은 아무것도 말해 주지 않는다.</b>
 *
 * <p><b>거리는 도메인이 낸다.</b> 화면에서 (대 − 현재가) ÷ 현재가 를 하면 그 산술이
 * {@code docs/adr/020} 이 금지한 "프론트가 만든 수" 가 된다.
 *
 * @param near 지금 가격에 가까운 쪽 모서리. 먼저 닿는 값이라 이쪽이 판단의 기준이다
 * @param far 반대쪽 모서리. 대에는 폭이 있고, 뚫렸는지는 이쪽까지 가 봐야 안다
 * @param touches 이 대에 몇 번 닿았나. 많을수록 사람이 실제로 반응한 자리다
 * @param distancePercent 지금 가격에서 가까운 모서리까지의 거리(%). 언제나 0 이상이다
 */
public record ZoneReadout(Price near, Price far, int touches, Percentage distancePercent) {

    private static final int PERCENT_SCALE = 4;

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    public ZoneReadout {
        DomainValues.required(near, "가까운 모서리");
        DomainValues.required(far, "먼 모서리");
        DomainValues.atLeast(touches, 1, "터치 횟수");
        DomainValues.required(distancePercent, "거리");
    }

    /**
     * 대를 지금 가격 기준으로 읽는다.
     *
     * <p>어느 모서리가 가까운지는 대와 가격의 위치가 정한다 — 지지대는 아래에 있으므로 위쪽
     * 모서리가 가깝고, 저항대는 그 반대다. 여기서 방향을 인자로 받지 않는 이유가 그것이다:
     * <b>이미 정해져 있는 것을 다시 물으면 부르는 쪽이 틀릴 수 있다.</b>
     */
    public static ZoneReadout of(PriceZone zone, Price close) {
        DomainValues.required(zone, "대");
        DomainValues.required(close, "현재가");
        Price low = zone.band().lower();
        Price high = zone.band().upper();
        boolean below = high.value().compareTo(close.value()) <= 0;
        Price near = below ? high : low;
        Price far = below ? low : high;
        return new ZoneReadout(near, far, zone.touches(), distance(near, close));
    }

    /** 가까운 모서리까지 몇 %. 부호를 싣지 않는다 — 위인지 아래인지는 지지/저항이 이미 말한다. */
    private static Percentage distance(Price near, Price close) {
        BigDecimal gap = near.value().subtract(close.value()).abs();
        return new Percentage(gap.divide(close.value(), MathContext.DECIMAL64)
                .multiply(HUNDRED)
                .setScale(PERCENT_SCALE, java.math.RoundingMode.HALF_UP));
    }
}
