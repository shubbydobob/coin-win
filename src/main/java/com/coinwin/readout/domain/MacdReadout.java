package com.coinwin.readout.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Money;
import com.coinwin.indicator.domain.AtrMultiple;
import com.coinwin.indicator.domain.IndicatorPoint;
import com.coinwin.indicator.domain.MacdValue;
import java.math.BigDecimal;
import java.util.List;

/**
 * MACD 가 지금 말하는 것.
 *
 * <p><b>부호 하나로 접히던 자리다.</b> 「시그널 위」 는 방금 넘어온 것과 스무 봉째 위에 있는
 * 것을 같은 사실로 만든다. 근거는 {@code docs/spec/indicator-usage.md} § 4.4.
 *
 * <p><b>히스토그램은 가격의 차라 ATR 로 나눈다.</b> 그대로 두면 60,000 시절과 100,000 시절이
 * 비교되지 않는다 — 이 저장소가 지표 값을 시대 너머로 견주려 할 때마다 걸린 자리다.
 *
 * @param histogram 히스토그램을 ATR 로 잰 값. <b>부호가 절반이다</b>
 * @param change 직전 봉 대비 히스토그램 변화. 붙는 중인지 벌어지는 중인지를 말한다
 * @param aboveZero MACD 선이 영선 위인가. 시그널 대비와 <b>다른 사실이다</b>
 * @param barsSinceCross 지금 부호가 이어진 봉 수. 부호가 방향이고 0 이면 히스토그램이 0 이다
 */
public record MacdReadout(
        AtrMultiple histogram, AtrMultiple change, boolean aboveZero, int barsSinceCross) {

    public MacdReadout {
        DomainValues.required(histogram, "히스토그램");
        DomainValues.required(change, "히스토그램 변화");
    }

    static MacdReadout over(List<IndicatorPoint<MacdValue>> points, Money atr) {
        MacdValue last = points.getLast().value();
        return new MacdReadout(
                AtrMultiple.of(Money.of(last.histogram()), atr),
                AtrMultiple.of(Money.of(last.histogram().subtract(previousHistogram(points))), atr),
                last.macd().signum() > 0,
                IndicatorDerivations.macdRuns(points).getLast());
    }

    /** 첫 봉에는 직전이 없다. 변화가 0 인 것과 같은 자리이므로 자기 값을 쓴다. */
    private static BigDecimal previousHistogram(List<IndicatorPoint<MacdValue>> points) {
        return points.get(Math.max(points.size() - 2, 0)).value().histogram();
    }

}
