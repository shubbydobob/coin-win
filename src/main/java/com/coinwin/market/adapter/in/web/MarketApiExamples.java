package com.coinwin.market.adapter.in.web;

/**
 * Swagger 예제 본문. 애너테이션 값은 컴파일 상수여야 하므로 한곳에 모은다.
 *
 * <p>숫자는 {@code MarketControllerTest} 의 페이크 거래소가 실제로 돌려주는 값이다.
 * 지어낸 예제는 필드가 바뀌어도 아무도 모르게 낡아 간다.
 */
final class MarketApiExamples {

    static final String METRICS_RESPONSE = """
            {
              "symbol": "BTCUSDT",
              "at": "2026-08-21T08:00:00Z",
              "fundingRatePercent": 0.010000,
              "openInterest": 81234.50000000,
              "longShortRatio": 1.8342
            }""";

    static final String CANDLES_RESPONSE = """
            {
              "symbol": "BTCUSDT",
              "interval": "1h",
              "count": 2,
              "candles": [
                {
                  "openTime": "2026-08-01T00:00:00Z",
                  "open": 60000.00, "high": 61000.00,
                  "low": 59000.00, "close": 60500.00,
                  "volume": 1.50000000
                },
                {
                  "openTime": "2026-08-01T01:00:00Z",
                  "open": 60000.00, "high": 61000.00,
                  "low": 59000.00, "close": 60501.00,
                  "volume": 1.50000000
                }
              ]
            }""";

    static final String SYNC_RESPONSE = """
            {
              "symbol": "BTCUSDT",
              "interval": "1h",
              "newlyStored": 24
            }""";

    private MarketApiExamples() {
    }
}
