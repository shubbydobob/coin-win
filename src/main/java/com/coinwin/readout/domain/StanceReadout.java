package com.coinwin.readout.domain;

import com.coinwin.indicator.domain.BollingerValue;
import com.coinwin.indicator.domain.IchimokuValue;
import com.coinwin.indicator.domain.IndicatorPoint;
import com.coinwin.indicator.domain.MacdValue;
import com.coinwin.market.domain.CandleSeries;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
 * <p><b>50 · 정배열 같은 경계는 관습이다.</b> 이 저장소가 검증한 수가 아니고, 검증한 적이
 * 없다는 사실이 화면 설명에 적혀 있어야 한다.
 */
final class StanceReadout {

    private StanceReadout() {
    }

    static List<IndicatorStance> over(TimeframeSeries series) {
        CandleSeries candles = series.candles();
        BigDecimal close = candles.isEmpty() ? null : candles.last().close().value();
        List<IndicatorStance> out = new ArrayList<>(5);
        out.add(ichimokuStance(close, last(series.ichimoku())));
        out.add(bollingerStance(close, last(series.bollinger())));
        out.add(movingAverageStance(series.movingAverages()));
        out.add(rsiStance(last(series.rsi())));
        out.add(macdStance(last(series.macd())));
        return List.copyOf(out);
    }

    private static IndicatorStance ichimokuStance(BigDecimal close, IchimokuValue value) {
        if (close == null || value == null) {
            return IndicatorStance.unknown("일목");
        }
        BigDecimal top = value.cloud().upper().value();
        BigDecimal bottom = value.cloud().lower().value();
        if (close.compareTo(top) > 0) {
            return new IndicatorStance("일목", Stance.LONG, "종가가 구름 위에 있다");
        }
        if (close.compareTo(bottom) < 0) {
            return new IndicatorStance("일목", Stance.SHORT, "종가가 구름 아래에 있다");
        }
        return new IndicatorStance("일목", Stance.NEUTRAL, "종가가 구름 안에 있다");
    }

    private static IndicatorStance bollingerStance(BigDecimal close, BollingerValue value) {
        if (close == null || value == null) {
            return IndicatorStance.unknown("볼린저");
        }
        if (close.compareTo(value.upper().value()) > 0) {
            return new IndicatorStance("볼린저", Stance.LONG, "종가가 상단 위에 있다");
        }
        if (close.compareTo(value.lower().value()) < 0) {
            return new IndicatorStance("볼린저", Stance.SHORT, "종가가 하단 아래에 있다");
        }
        return new IndicatorStance("볼린저", Stance.NEUTRAL, "종가가 밴드 안에 있다");
    }

    /**
     * <b>정배열은 순서일 뿐이다.</b> 20 &gt; 50 &gt; 200 이면 짧은 평균이 긴 평균 위라는
     * 사실이고, 그것이 계속된다는 뜻은 아니다.
     */
    private static IndicatorStance movingAverageStance(List<MovingAverageLine> averages) {
        List<BigDecimal> values = averages.stream()
                .map(line -> line.points().isEmpty() ? null
                        : line.points().get(line.points().size() - 1).value().value())
                .toList();
        if (values.size() < 3 || values.contains(null)) {
            return IndicatorStance.unknown("이동평균");
        }
        BigDecimal fast = values.get(0);
        BigDecimal mid = values.get(1);
        BigDecimal slow = values.get(2);
        if (fast.compareTo(mid) > 0 && mid.compareTo(slow) > 0) {
            return new IndicatorStance("이동평균", Stance.LONG, "20 > 50 > 200 으로 놓여 있다");
        }
        if (fast.compareTo(mid) < 0 && mid.compareTo(slow) < 0) {
            return new IndicatorStance("이동평균", Stance.SHORT, "20 < 50 < 200 으로 놓여 있다");
        }
        return new IndicatorStance("이동평균", Stance.NEUTRAL, "셋이 순서대로 놓여 있지 않다");
    }

    private static IndicatorStance rsiStance(com.coinwin.common.domain.Percentage value) {
        if (value == null) {
            return IndicatorStance.unknown("RSI");
        }
        int side = value.value().compareTo(BigDecimal.valueOf(50));
        String reading = value.value().toPlainString();
        if (side > 0) {
            return new IndicatorStance("RSI", Stance.LONG, reading + " 로 50 위에 있다");
        }
        if (side < 0) {
            return new IndicatorStance("RSI", Stance.SHORT, reading + " 로 50 아래에 있다");
        }
        return new IndicatorStance("RSI", Stance.NEUTRAL, "정확히 50 이다");
    }

    private static IndicatorStance macdStance(MacdValue value) {
        if (value == null) {
            return IndicatorStance.unknown("MACD");
        }
        int side = value.histogram().signum();
        if (side > 0) {
            return new IndicatorStance("MACD", Stance.LONG, "시그널 위에 있다");
        }
        if (side < 0) {
            return new IndicatorStance("MACD", Stance.SHORT, "시그널 아래에 있다");
        }
        return new IndicatorStance("MACD", Stance.NEUTRAL, "시그널과 같다");
    }

    private static <T> T last(List<IndicatorPoint<T>> points) {
        return Optional.ofNullable(points)
                .filter(list -> !list.isEmpty())
                .map(list -> list.get(list.size() - 1).value())
                .orElse(null);
    }
}
