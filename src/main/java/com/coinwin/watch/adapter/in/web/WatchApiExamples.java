package com.coinwin.watch.adapter.in.web;

/**
 * Swagger 예제 본문. 애너테이션 값은 컴파일 상수여야 하므로 한곳에 모은다.
 *
 * <p>날짜는 커밋된 스냅샷에 실제로 들어 있는 것이다. 지어낸 예제는 필드가 바뀌어도 아무도
 * 모르게 낡아 간다.
 */
final class WatchApiExamples {

    static final String CALENDAR_RESPONSE = """
            {
              "now": "2026-08-23T12:00:00Z",
              "stale": false,
              "events": [
                {
                  "kind": "CPI",
                  "at": "2026-09-11T12:30:00Z",
                  "title": "미국 CPI (8월분)",
                  "importance": "HIGH",
                  "until": "PT456H30M",
                  "warning": false
                },
                {
                  "kind": "FOMC",
                  "at": "2026-09-16T18:00:00Z",
                  "title": "FOMC 금리 결정 + 경제전망(SEP)",
                  "importance": "HIGH",
                  "until": "PT582H",
                  "warning": false
                }
              ]
            }""";

    private WatchApiExamples() {
    }
}
