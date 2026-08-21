package com.coinwin.position.api;

/**
 * Swagger 예제 본문. 애너테이션 값은 컴파일 상수여야 하므로 한곳에 모은다.
 *
 * <p>예제는 roadmap.md Phase 1 완료 조건의 시나리오 그대로다 — 잔고 800 USDT, 50% 분할,
 * 60,000 / 58,000 진입. 문서의 숫자와 테스트의 숫자가 갈라지지 않게 하려는 것이다.
 */
final class PositionApiExamples {

    static final String REQUEST = """
            {
              "direction": "LONG",
              "entries": [
                { "price": 60000, "allocation": 50 },
                { "price": 58000, "allocation": 50 }
              ],
              "stopLoss": 56000,
              "takeProfit": 66000,
              "leverage": 10,
              "accountBalance": 800,
              "riskPercent": 2
            }""";

    static final String RESPONSE = """
            {
              "fillStates": [
                {
                  "filledEntries": 1,
                  "averageEntryPrice": 60000.00,
                  "quantity": 0.00266667,
                  "liquidationPrice": 54216.87,
                  "maxLoss": 10.67
                },
                {
                  "filledEntries": 2,
                  "averageEntryPrice": 59000.00,
                  "quantity": 0.00533333,
                  "liquidationPrice": 53313.25,
                  "maxLoss": 16.00
                }
              ],
              "requiredMargin": 31.47,
              "riskRewardRatio": 2.33,
              "weakRiskReward": false,
              "marginExceedsBalance": false
            }""";

    private PositionApiExamples() {
    }
}
