package com.coinwin.market;

import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import com.coinwin.market.domain.Candle;
import com.coinwin.market.domain.CandleInterval;
import com.coinwin.market.domain.CandleQuery;
import com.coinwin.market.domain.CandleSeries;
import com.coinwin.market.domain.Symbol;
import com.coinwin.market.domain.TimeRange;
import java.time.Instant;
import java.util.stream.IntStream;

/**
 * 캔들 테스트 데이터. 계약 스위트와 서비스 테스트가 같은 것을 쓴다.
 *
 * <p>양쪽이 각자 만들면 "5개 저장하고 5개 읽는다" 의 5개가 서로 다른 캔들이 되고, 어느 쪽
 * 기대값이 맞는지 판단할 근거가 사라진다. conventions.md: 두 번째 나타나면 즉시 추출한다.
 *
 * <p>{@code index} 는 T0 부터 몇 번째 캔들인가다. 종가에 index 를 실어 어느 캔들이 돌아왔는지
 * 구분할 수 있게 한다.
 */
public final class MarketFixtures {

    public static final Symbol SYMBOL = Symbol.BTC_USDT;
    public static final CandleInterval INTERVAL = CandleInterval.ONE_HOUR;
    public static final Instant T0 = Instant.parse("2026-08-01T00:00:00Z");

    private MarketFixtures() {
    }

    public static Instant hour(int index) {
        return T0.plus(INTERVAL.length().multipliedBy(index));
    }

    public static Candle candleAt(int index) {
        return new Candle(hour(index), Price.of("60000"), Price.of("61000"),
                Price.of("59000"), Price.of(String.valueOf(60000 + index)), Quantity.of("1.5"));
    }

    public static CandleSeries candles(int fromIndex, int toIndexExclusive) {
        return new CandleSeries(IntStream.range(fromIndex, toIndexExclusive)
                .mapToObj(MarketFixtures::candleAt)
                .toList());
    }

    public static CandleQuery query(int fromIndex, int toIndexExclusive) {
        return new CandleQuery(SYMBOL, INTERVAL,
                new TimeRange(hour(fromIndex), hour(toIndexExclusive)));
    }
}
