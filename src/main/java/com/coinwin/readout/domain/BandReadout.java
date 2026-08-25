package com.coinwin.readout.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Percentage;
import com.coinwin.common.domain.Price;
import com.coinwin.indicator.domain.PriceBand;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * 가격 구간 하나를 지금 가격 기준으로 읽은 것.
 *
 * <p><b>대와 매물대가 같은 것을 묻는다.</b> 둘 다 폭이 있는 구간이고, 둘 다 "먼저 닿는 모서리는
 * 어느 쪽이고 거기까지 몇 %인가" 를 답해야 한다. 근거는 다르지만(터치 횟수 대 거래량) 읽는
 * 방식이 같으므로 규칙을 여기 한 번만 둔다 — 두 곳에 두면 한쪽만 고쳐지는 날 화면의 두 줄이
 * 다른 뜻이 된다.
 *
 * <p><b>거리는 도메인이 낸다.</b> 화면에서 (모서리 − 현재가) ÷ 현재가 를 하면 그 산술이
 * {@code docs/adr/020} 이 금지한 "프론트가 만든 수" 가 된다.
 *
 * @param near 지금 가격에 먼저 닿는 모서리
 * @param far 반대쪽 모서리. 구간을 뚫었는지는 이쪽까지 가 봐야 안다
 * @param distancePercent 가까운 모서리까지 몇 %. 언제나 0 이상이다
 */
public record BandReadout(Price near, Price far, Percentage distancePercent) {

    private static final int PERCENT_SCALE = 4;

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    public BandReadout {
        DomainValues.required(near, "가까운 모서리");
        DomainValues.required(far, "먼 모서리");
        DomainValues.required(distancePercent, "거리");
    }

    /**
     * 어느 모서리가 가까운지는 구간과 가격의 위치가 정한다 — 아래에 있는 구간은 위쪽 모서리가
     * 가깝고 위에 있는 구간은 그 반대다. <b>방향을 인자로 받지 않는 이유가 그것이다:</b> 이미
     * 정해져 있는 것을 다시 물으면 부르는 쪽이 틀릴 수 있다.
     */
    public static BandReadout of(PriceBand band, Price close) {
        DomainValues.required(band, "가격 구간");
        DomainValues.required(close, "현재가");
        boolean below = band.upper().value().compareTo(close.value()) <= 0;
        Price near = below ? band.upper() : band.lower();
        Price far = below ? band.lower() : band.upper();
        return new BandReadout(near, far, distance(near, close));
    }

    /** 부호를 싣지 않는다 — 위인지 아래인지는 부르는 쪽이 이미 알고 있다. */
    private static Percentage distance(Price near, Price close) {
        BigDecimal gap = near.value().subtract(close.value()).abs();
        return new Percentage(gap.divide(close.value(), MathContext.DECIMAL64)
                .multiply(HUNDRED)
                .setScale(PERCENT_SCALE, RoundingMode.HALF_UP));
    }
}
