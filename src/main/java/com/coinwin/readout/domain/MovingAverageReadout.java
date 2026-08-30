package com.coinwin.readout.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Price;
import com.coinwin.indicator.domain.AtrMultiple;
import com.coinwin.indicator.domain.IndicatorPoint;
import com.coinwin.indicator.domain.InsufficientCandlesException;
import com.coinwin.indicator.domain.MovingAverage;
import com.coinwin.market.domain.CandleSeries;
import java.util.List;
import java.util.Optional;

/**
 * 이동평균이 지금 말하는 것.
 *
 * <p><b>「정배열」 은 순서일 뿐이다.</b> 20 이 200 보다 1 위인 것과 다섯 배 벌어진 것이 같은
 * 딱지를 받는다. 벌어진 정도를 함께 내면 그 둘이 갈린다.
 * 근거는 {@code docs/spec/indicator-usage.md} § 4.5.
 *
 * <p><b>비어 있을 수 있다.</b> 200 구간은 봉 200 개가 있어야 값을 낸다 — 없는 것을 0 으로
 * 적으면 "두 선이 붙어 있다" 는 없는 사실이 생긴다.
 *
 * @param spread (20 − 200) 을 ATR 로 잰 값. 부호가 어느 쪽이 위인지를 말한다
 */
public record MovingAverageReadout(Optional<AtrMultiple> spread) {

    /** 벌어진 정도를 재는 두 구간. <b>딱지가 쓰는 셋 중 양 끝이다.</b> */
    private static final int FAST = 20;

    private static final int SLOW = 200;

    public MovingAverageReadout {
        DomainValues.required(spread, "이동평균 간격");
    }

    static MovingAverageReadout over(CandleSeries series, Money atr) {
        Optional<Price> fast = last(series, FAST);
        Optional<Price> slow = last(series, SLOW);
        return new MovingAverageReadout(
                fast.flatMap(one -> slow.map(other -> AtrMultiple.between(one, other, atr))));
    }

    /**
     * 그 구간의 마지막 값. <b>봉이 모자라면 비어 있다.</b>
     *
     * <p>워밍업을 여기서 다시 세지 않는다 — 몇 봉이 필요한지는 계산기가 알고, 여기 같은
     * 규칙을 또 두면 구간을 바꾸는 날 한쪽만 낡는다.
     */
    private static Optional<Price> last(CandleSeries series, int period) {
        try {
            List<IndicatorPoint<Price>> points = MovingAverage.simple(period).over(series);
            return Optional.of(points.getLast().value());
        } catch (InsufficientCandlesException absent) {
            return Optional.empty();
        }
    }
}
