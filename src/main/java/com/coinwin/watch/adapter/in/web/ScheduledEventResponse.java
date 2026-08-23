package com.coinwin.watch.adapter.in.web;

import com.coinwin.watch.domain.ScheduledEvent;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * 예정된 이벤트 하나.
 *
 * <p><b>남은 시간과 경고 여부를 서버가 낸다.</b> 화면이 계산하면 "사흘 안이면 경고" 라는 규칙이
 * 두 곳에 생긴다({@code docs/adr/020}).
 *
 * <p><b>경고는 규모에 대한 것이지 방향에 대한 것이 아니다.</b> "그날은 명목을 줄여라" 이지
 * "오를 것이다" 가 아니다.
 */
@Schema(description = "예정된 이벤트")
public record ScheduledEventResponse(

        @Schema(description = "종류", example = "FOMC",
                allowableValues = {"FOMC", "CPI", "QRA", "OTHER"})
        String kind,

        @Schema(description = "발표 시각 (UTC)", example = "2026-09-16T18:00:00Z")
        Instant at,

        @Schema(description = "무엇이 발표되는가", example = "FOMC 금리 결정 + 경제전망(SEP)")
        String title,

        @Schema(description = "사전 경고 대상인가", example = "HIGH",
                allowableValues = {"HIGH", "NORMAL"})
        String importance,

        @Schema(description = "지금부터 남은 시간 (ISO-8601 기간)", example = "PT576H")
        String until,

        @Schema(description = "지금 경고할 때인가. 중요한 것이 사흘 안일 때만 참이다",
                example = "false")
        boolean warning) {

    static ScheduledEventResponse from(ScheduledEvent event, Instant now) {
        return new ScheduledEventResponse(
                event.kind().name(),
                event.at(),
                event.title(),
                event.importance().name(),
                event.until(now).toString(),
                event.needsWarning(now));
    }
}
