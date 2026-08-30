package com.coinwin.readout.domain;

import com.coinwin.common.domain.Percentage;
import com.coinwin.indicator.domain.BollingerValue;
import com.coinwin.indicator.domain.IchimokuValue;
import com.coinwin.indicator.domain.MacdValue;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 다섯 지표가 <b>지금</b> 어느 쪽에 서 있는지를 낸다.
 *
 * <p><b>규칙이 전부 정의다.</b> 종가가 구름 위인가, 밴드 밖인가, 이동평균이 순서대로 놓였는가,
 * RSI 가 50 을 넘는가, MACD 가 시그널 위인가. 어느 것도 미래를 말하지 않는다.
 *
 * <p><b>이 판정을 화면에 두지 않은 이유.</b> 프론트가 세 이동평균을 비교해 "정배열" 이라고
 * 적으면 그 규칙이 화면에 생기고, 그러면 같은 규칙이 백테스트나 연구 쪽과 갈라진다. 위치
 * 판정을 {@code BandPosition} 으로 도메인에 둔 것과 같은 판단이다.
 *
 * <p><b>아무 봉 시점을 물을 수 있다.</b> 화면은 마지막 봉만 쓰지만, "이 자리에 섰을 때
 * 다음에 무슨 일이 있었나" 를 재려면 과거의 모든 봉에서 같은 판정이 나와야 한다. 그 판정이
 * 재는 쪽에 복사되면 화면과 측정이 다른 규칙을 쓰게 된다.
 *
 * <p><b>50 · 정배열 같은 경계는 관습이다.</b> 이 저장소가 검증한 수가 아니고, 검증한 적이
 * 없다는 사실이 화면 설명에 적혀 있어야 한다.
 *
 * <p><b>다섯을 한 번에 세지 않는다.</b> 지표마다 {@link IndicatorFamily} 가 붙어 있고, 세는
 * 것은 무리 안에서만 뜻이 있다 — 근거는 {@code docs/spec/indicator-usage.md} § 2 다.
 */
final class StanceReadout {

    private StanceReadout() {
    }

    static List<IndicatorStance> at(TimeframeSeries series, Instant bar) {
        BigDecimal close = BarLookup.closeAt(series.candles(), bar);
        List<IndicatorStance> out = new ArrayList<>(5);
        out.add(ichimokuStance(close, BarLookup.valueAt(series.ichimoku(), bar)));
        out.add(bollingerStance(close, BarLookup.valueAt(series.bollinger(), bar)));
        out.add(movingAverageStance(series.movingAverages(), bar));
        out.add(rsiStance(BarLookup.valueAt(series.rsi(), bar)));
        out.add(macdStance(BarLookup.valueAt(series.macd(), bar)));
        return List.copyOf(out);
    }

    private static IndicatorStance ichimokuStance(BigDecimal close, IchimokuValue value) {
        if (close == null || value == null) {
            return IndicatorStance.unknown(IndicatorKind.ICHIMOKU);
        }
        BigDecimal top = value.cloud().upper().value();
        BigDecimal bottom = value.cloud().lower().value();
        if (close.compareTo(top) > 0) {
            return stance(IndicatorKind.ICHIMOKU, Stance.LONG, "종가가 구름 위에 있다");
        }
        if (close.compareTo(bottom) < 0) {
            return stance(IndicatorKind.ICHIMOKU, Stance.SHORT, "종가가 구름 아래에 있다");
        }
        return stance(IndicatorKind.ICHIMOKU, Stance.NEUTRAL, "종가가 구름 안에 있다");
    }

    private static IndicatorStance bollingerStance(BigDecimal close, BollingerValue value) {
        if (close == null || value == null) {
            return IndicatorStance.unknown(IndicatorKind.BOLLINGER);
        }
        if (close.compareTo(value.upper().value()) > 0) {
            return stance(IndicatorKind.BOLLINGER, Stance.LONG, "종가가 상단 위에 있다");
        }
        if (close.compareTo(value.lower().value()) < 0) {
            return stance(IndicatorKind.BOLLINGER, Stance.SHORT, "종가가 하단 아래에 있다");
        }
        return stance(IndicatorKind.BOLLINGER, Stance.NEUTRAL, "종가가 밴드 안에 있다");
    }

    /**
     * <b>정배열은 순서일 뿐이다.</b> 20 &gt; 50 &gt; 200 이면 짧은 평균이 긴 평균 위라는
     * 사실이고, 그것이 계속된다는 뜻은 아니다.
     */
    private static IndicatorStance movingAverageStance(
            List<MovingAverageLine> averages, Instant bar) {
        BigDecimal fast = BarLookup.movingAverageAt(averages, 20, bar);
        BigDecimal mid = BarLookup.movingAverageAt(averages, 50, bar);
        BigDecimal slow = BarLookup.movingAverageAt(averages, 200, bar);
        if (fast == null || mid == null || slow == null) {
            return IndicatorStance.unknown(IndicatorKind.MOVING_AVERAGE);
        }
        if (fast.compareTo(mid) > 0 && mid.compareTo(slow) > 0) {
            return stance(IndicatorKind.MOVING_AVERAGE, Stance.LONG, "20 > 50 > 200 으로 놓여 있다");
        }
        if (fast.compareTo(mid) < 0 && mid.compareTo(slow) < 0) {
            return stance(IndicatorKind.MOVING_AVERAGE, Stance.SHORT, "20 < 50 < 200 으로 놓여 있다");
        }
        return stance(IndicatorKind.MOVING_AVERAGE, Stance.NEUTRAL, "셋이 순서대로 놓여 있지 않다");
    }

    private static IndicatorStance rsiStance(Percentage value) {
        if (value == null) {
            return IndicatorStance.unknown(IndicatorKind.RSI);
        }
        int side = value.value().compareTo(BigDecimal.valueOf(50));
        String reading = value.value().toPlainString();
        if (side > 0) {
            return stance(IndicatorKind.RSI, Stance.LONG, reading + " 로 50 위에 있다");
        }
        if (side < 0) {
            return stance(IndicatorKind.RSI, Stance.SHORT, reading + " 로 50 아래에 있다");
        }
        return stance(IndicatorKind.RSI, Stance.NEUTRAL, "정확히 50 이다");
    }

    private static IndicatorStance macdStance(MacdValue value) {
        if (value == null) {
            return IndicatorStance.unknown(IndicatorKind.MACD);
        }
        int side = value.histogram().signum();
        if (side > 0) {
            return stance(IndicatorKind.MACD, Stance.LONG, "시그널 위에 있다");
        }
        if (side < 0) {
            return stance(IndicatorKind.MACD, Stance.SHORT, "시그널 아래에 있다");
        }
        return stance(IndicatorKind.MACD, Stance.NEUTRAL, "시그널과 같다");
    }

    private static IndicatorStance stance(IndicatorKind kind, Stance side, String statement) {
        return new IndicatorStance(kind, side, statement);
    }
}
