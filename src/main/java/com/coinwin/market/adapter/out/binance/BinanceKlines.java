package com.coinwin.market.adapter.out.binance;

import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import com.coinwin.market.domain.Candle;
import com.coinwin.market.domain.CandleSeries;
import java.time.Instant;
import java.util.Arrays;

/**
 * {@code /fapi/v1/klines} 응답을 캔들로 옮긴다.
 *
 * <p>응답은 이름 없는 배열의 배열이다. 어느 자리가 무엇인지는 <b>문서에만</b> 있고 값에는
 * 없다. 그래서 자리 번호를 상수로 못 박는다 — {@code row[4]} 가 코드 여기저기에 흩어지면
 * 종가와 거래량이 뒤바뀌어도 컴파일도 테스트도 통과한다.
 *
 * <pre>
 * [ 1754006400000, "60000.00", "61000.00", "59000.00", "60500.00", "12.5", ... ]
 *     openTime        open        high        low         close      volume
 * </pre>
 */
final class BinanceKlines {

    private static final int OPEN_TIME = 0;
    private static final int OPEN = 1;
    private static final int HIGH = 2;
    private static final int LOW = 3;
    private static final int CLOSE = 4;
    private static final int VOLUME = 5;

    /** 자리 수가 이보다 적으면 응답 형식이 바뀐 것이다. 조용히 넘기지 않는다. */
    private static final int MINIMUM_FIELDS = VOLUME + 1;

    private BinanceKlines() {
    }

    static CandleSeries toSeries(Object[][] rows) {
        if (rows == null) {
            return CandleSeries.empty();
        }
        return new CandleSeries(Arrays.stream(rows).map(BinanceKlines::toCandle).toList());
    }

    private static Candle toCandle(Object[] row) {
        if (row.length < MINIMUM_FIELDS) {
            throw new BinanceResponseException(
                    "klines 응답의 자리 수가 모자란다: " + row.length + "개");
        }
        return new Candle(
                Instant.ofEpochMilli(Long.parseLong(text(row, OPEN_TIME))),
                Price.of(text(row, OPEN)),
                Price.of(text(row, HIGH)),
                Price.of(text(row, LOW)),
                Price.of(text(row, CLOSE)),
                Quantity.of(text(row, VOLUME)));
    }

    /** 가격은 문자열, 시각은 숫자로 온다. 둘 다 문자열로 받아 값 객체가 파싱하게 한다. */
    private static String text(Object[] row, int index) {
        return String.valueOf(row[index]);
    }
}
