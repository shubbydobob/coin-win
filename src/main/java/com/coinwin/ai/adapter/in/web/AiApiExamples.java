package com.coinwin.ai.adapter.in.web;

/**
 * Swagger 예제 본문. 상수로 빼 두는 이유는 애너테이션 안에 긴 JSON 을 쓰면 DTO 가 읽히지
 * 않기 때문이다. {@code journal.adapter.in.web} 의 {@code JournalApiExamples} 와 같은 이유다.
 *
 * <p>응답 예제가 {@code TradePlanRequest} 의 예제와 <b>같은 모양</b>인 것은 의도된 것이다.
 * 초안을 그대로 잘라 매매 기록 API 에 붙여 넣을 수 있어야 한다.
 */
final class AiApiExamples {

    static final String PLAN_DRAFT_REQUEST = """
            {
              "text": "6만2천에 절반, 6만에 절반 롱. 손절 5만8천, 익절 6만8천, 10배로 간다"
            }""";

    static final String PLAN_DRAFT_RESPONSE = """
            {
              "direction": "LONG",
              "entries": [
                {"price": 62000, "allocation": 50},
                {"price": 60000, "allocation": 50}
              ],
              "stopLoss": 58000,
              "takeProfit": 68000,
              "leverage": 10
            }""";

    static final String JOURNAL_QUERY_REQUEST = """
            {
              "question": "손실 직후에 들어간 거래는 결과가 어땠나?",
              "topK": 8
            }""";

    static final String REINDEX_RESPONSE = """
            {
              "indexed": 37
            }""";

    private AiApiExamples() {
    }
}
