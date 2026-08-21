package com.coinwin.journal.adapter.in.web;

/**
 * Swagger 예제 본문. 상수로 빼 두는 이유는 애너테이션 안에 긴 JSON 을 쓰면 DTO 가 읽히지
 * 않기 때문이다. {@code position.api} 의 {@code PositionApiExamples} 와 같은 이유다.
 *
 * <p>숫자는 실제로 맞물린다 — 평단 59,500 / 수량 0.1 / 익절 64,000 이면 총손익 450.00 이고
 * 수수료·펀딩비 6.20 을 빼면 실현 손익이 443.80 이다. 예제가 계산과 어긋나면 문서가 거짓말을 한다.
 */
final class JournalApiExamples {

    static final String PLAN_REQUEST = """
            {
              "direction": "LONG",
              "entries": [
                {"price": 60000, "allocation": 50},
                {"price": 59000, "allocation": 50}
              ],
              "stopLoss": 58000,
              "takeProfit": 64000,
              "leverage": 10
            }""";

    static final String FILLS_REQUEST = """
            {
              "fills": [
                {"price": 60000, "quantity": 0.05, "at": "2026-08-01T01:00:00Z"},
                {"price": 59000, "quantity": 0.05, "at": "2026-08-01T02:00:00Z"}
              ],
              "context": {
                "priceAtEntry": 60000,
                "ichimokuPosition": "ABOVE",
                "bollingerPosition": "INSIDE",
                "rationale": "4h 59,000 지지 3회 확인"
              }
            }""";

    static final String CLOSE_REQUEST = """
            {
              "exitPrice": 64000,
              "exitAt": "2026-08-01T09:00:00Z",
              "exitReason": "PLANNED_TARGET",
              "fees": 5.00,
              "funding": 1.20
            }""";

    private JournalApiExamples() {
    }
}
