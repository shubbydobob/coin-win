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
     * 두 가격대 사이를 오가는 톱니. <b>대가 실제로 생기는 유일한 모양</b>이다.
     *
     * <p>지표 픽스처의 단조 시리즈에는 피벗이 하나도 없어 백테스트가 거래를 한 건도 하지 않는다.
     * 여기서는 다리마다 전환점을 조금씩 흘려 <b>이웃한 극값의 동률을 피한다</b> — 같은 값이
     * 연달아 나오면 "동률은 피벗이 아니다" 규칙에 걸려 역시 대가 생기지 않는다.
     *
     * <p>흘리는 양이 {@code leg % 5} 로 <b>순환</b>하는 것이 중요하다. 단조로 흘리면 새 저점이
     * 언제나 이전 저점보다 높아 가격이 과거 지지대로 되돌아오지 않고, 그러면 신호는 서는데
     * 체결은 한 번도 되지 않는다. 되돌아오는 것이 지지·저항 매매의 전제다.
     *
     * <p>동률 회피는 이웃 {@code lookback} 봉 안에서만 필요하므로 멀리 떨어진 극값이 같은 값을
     * 가져도 무방하다 — 오히려 그것이 같은 대에 터치가 쌓이는 실제 모습이다.
     *
     * <p>난수를 쓰지 않는다. 결정론 테스트의 입력이 결정론적이지 않으면 아무것도 증명하지 못한다.
     */
    public static CandleSeries zigzag(int legs, int legLength, int low, int high) {
        List<Integer> closes = new ArrayList<>();
        int current = low;
        for (int leg = 0; leg < legs; leg++) {
            int target = (leg % 2 == 0 ? high : low) + (leg % 5) * 7;
            int stepSize = (target - current) / legLength;
            for (int step = 0; step < legLength; step++) {
                current += stepSize;
                closes.add(current);
            }
        }
        return wave(30, closes.stream().mapToInt(Integer::intValue).toArray());
    }

    /**
     * 톱니 뒤에 하락 추세를 붙인 시리즈. <b>손절이 나오는 유일한 모양</b>이다.
     *
     * <p>순수한 톱니에서는 가격이 반드시 되돌아오므로 익절만 나오고 진 거래가 하나도 없다.
     * 그러면 손익비도 낙폭도 검증할 수 없다. 지지대가 실제로 뚫리는 구간이 있어야 전략의
     * 나쁜 쪽이 드러난다.
     */
    public static CandleSeries zigzagThenBreakdown(CandleSeries zigzag, int tailBars) {
        List<Candle> candles = new ArrayList<>(zigzag.candles());
        int close = candles.getLast().close().value().intValue();
        for (int i = 0; i < tailBars; i++) {
            close -= 250;
            candles.add(candle(candles.size(), String.valueOf(close + 30),
                    String.valueOf(close - 30), String.valueOf(close)));
        }
        return new CandleSeries(candles);
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
