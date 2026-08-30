package com.coinwin.readout.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Percentage;
import com.coinwin.indicator.domain.IndicatorPoint;
import java.math.BigDecimal;
import java.util.List;

/**
 * RSI 가 지금 말하는 것.
 *
 * <p><b>50 으로 접히던 자리다.</b> 「50 위」 는 51 과 78 을 같은 사실로 만든다. 딱지가 그
 * 경계를 쓰는 것은 그것이 관습이기 때문이고, <b>이 저장소가 검증한 수가 아니다.</b>
 *
 * <p><b>차트가 이미 곡선을 그리고 있는데도 값을 따로 내는 이유</b>는 판독이 <b>세 주기를
 * 나란히</b> 놓기 때문이다. 차트는 한 번에 한 주기만 보여 주므로 "15분은 78 인데 4시간은
 * 52" 라는 비교가 그림으로는 성립하지 않는다.
 *
 * @param value 지금 RSI. <b>50 으로 접지 않는다</b>
 * @param change3 3봉 전 대비 변화(%p). <b>비율이 아니라 비율의 차라 음수가 될 수 있다</b> —
 *     {@code MacdValue} 가 가격의 차를 그대로 든 것과 같은 이유로 {@link Percentage} 가 아니다
 */
public record RsiReadout(Percentage value, BigDecimal change3) {

    /** 몇 봉 전과 견주는가. <b>관습이 아니라 임의로 고른 수다</b> — 검증한 적이 없다. */
    private static final int LOOKBACK = 3;

    private static final int SCALE = 4;

    public RsiReadout {
        DomainValues.required(value, "RSI");
        change3 = DomainValues.scaled(change3, SCALE, "RSI 변화");
    }

    static RsiReadout over(List<IndicatorPoint<Percentage>> points) {
        BigDecimal now = points.getLast().value().value();
        BigDecimal before = points.get(Math.max(points.size() - 1 - LOOKBACK, 0)).value().value();
        return new RsiReadout(points.getLast().value(), now.subtract(before));
    }
}
