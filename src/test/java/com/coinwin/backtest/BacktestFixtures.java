package com.coinwin.backtest;

import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import com.coinwin.market.domain.Candle;
import com.coinwin.market.domain.CandleInterval;
import com.coinwin.market.domain.CandleSeries;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 백테스트 테스트용 캔들.
 *
 * <p>{@code IndicatorFixtures} 를 쓰지 않는 이유는 <b>필요한 캔들의 모양이 반대</b>이기
 * 때문이다. 지표 golden test 는 닫힌 식이 나오는 단조 시리즈를 쓰지만, 지지·저항은 정의상
 * <b>같은 자리로 여러 번 되돌아오는</b> 비단조 시리즈에서만 생긴다. 단조 시리즈에는 피벗이
 * 하나도 없다.
 *
 * <p>{@link #wave} 가 그 모양을 만든다 — 지정한 종가 열을 따라가며 각 봉을 고가·저가로 감싼다.
 */
public final class BacktestFixtures {

    public static final Instant T0 = Instant.parse("2026-08-01T00:00:00Z");
    public static final CandleInterval INTERVAL = CandleInterval.ONE_HOUR;

    private BacktestFixtures() {
    }

    public static Instant hour(int index) {
        return T0.plus(INTERVAL.length().multipliedBy(index));
    }

    /** 고가·저가·종가를 직접 지정한 캔들. 시가는 종가와 같다. */
    public static Candle candle(int index, String high, String low, String close) {
        return new Candle(hour(index), Price.of(close), Price.of(high), Price.of(low),
                Price.of(close), Quantity.of("1"));
    }

    /**
     * 시가까지 지정한 캔들. 갭과 봉 내부 순서를 검사할 때 쓴다.
     *
     * <p>네 값을 {@code "시가/고가/저가/종가"} 한 문자열로 받는다. 파라미터 네 개를 나열하면
     * 한계(4개)에 걸리기도 하지만, 그보다 <b>호출부에서 순서를 잘못 적어도 컴파일이 되기</b>
     * 때문이다. 갭 테스트는 시가와 종가의 차이가 전부라 그 실수가 조용히 통과한다.
     */
    public static Candle ohlc(int index, String slashSeparated) {
        String[] parts = slashSeparated.split("/");
        if (parts.length != 4) {
            throw new IllegalArgumentException("시가/고가/저가/종가 네 값이 필요하다: " + slashSeparated);
        }
        return new Candle(hour(index), Price.of(parts[0]), Price.of(parts[1]),
                Price.of(parts[2]), Price.of(parts[3]), Quantity.of("1"));
    }

    /**
     * 종가 열을 {@code half} 만큼 감싼 캔들 묶음.
     *
     * <p>고가 = 종가 + half, 저가 = 종가 − half 이므로 종가 열의 극값이 곧 고가·저가의
     * 극값이다. 피벗 위치를 손으로 셀 수 있다.
     */
    public static CandleSeries wave(int half, int... closes) {
        List<Candle> candles = new ArrayList<>();
        for (int i = 0; i < closes.length; i++) {
            candles.add(candle(i, String.valueOf(closes[i] + half),
                    String.valueOf(closes[i] - half), String.valueOf(closes[i])));
        }
        return new CandleSeries(candles);
    }
}
