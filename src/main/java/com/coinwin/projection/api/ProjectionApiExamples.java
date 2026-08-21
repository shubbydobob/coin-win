package com.coinwin.projection.api;

/**
 * Swagger 예제 본문. 애너테이션 값은 컴파일 상수여야 하므로 한곳에 모은다.
 *
 * <p>숫자는 지어낸 것이 아니라 {@code ProjectionControllerTest} 가 같은 요청으로 실제로 받는
 * 응답이다. 시드가 고정되어 있으므로 문서의 숫자와 테스트의 숫자는 갈라질 수 없다.
 *
 * <p>조건은 scope.md 의 전제를 따른다 — 증거금 800 USDT, 거래당 2%.
 */
final class ProjectionApiExamples {

    static final String CURVE_REQUEST = """
            {
              "spec": {
                "initialCapital": 800,
                "winRate": 45,
                "riskRewardRatio": 2,
                "riskPerTrade": 2,
                "tradesPerWeek": 1,
                "weeks": 4
              },
              "seed": 20260821
            }""";

    static final String CURVE_RESPONSE = """
            {
              "equity": [800.00, 832.00, 865.28, 899.89, 881.89],
              "trades": 4,
              "finalEquity": 881.89,
              "maxDrawdown": 2.0002
            }""";

    static final String MONTE_CARLO_REQUEST = """
            {
              "spec": {
                "initialCapital": 800,
                "winRate": 45,
                "riskRewardRatio": 2,
                "riskPerTrade": 2,
                "tradesPerWeek": 2,
                "weeks": 50
              },
              "runs": 1000,
              "seed": 20260821
            }""";

    static final String MONTE_CARLO_RESPONSE = """
            {
              "runs": 1000,
              "tradesPerRun": 100,
              "expectancyPerTrade": 0.350000,
              "worstEquity": 594.44,
              "percentile5Equity": 956.24,
              "medianEquity": 1538.24,
              "percentile95Equity": 2474.47,
              "bestEquity": 3980.53,
              "medianMaxDrawdown": 14.9237,
              "worstMaxDrawdown": 42.9287,
              "lossProbability": 0.8000
            }""";

    private ProjectionApiExamples() {
    }
}
