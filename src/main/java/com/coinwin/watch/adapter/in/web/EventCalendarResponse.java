package com.coinwin.watch.adapter.in.web;

import com.coinwin.watch.domain.EventCalendar;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * 무엇이 다가오고 있나.
 *
 * <p><b>지난 이벤트는 실리지 않는다.</b> 남겨 두면 화면이 그것을 걸러야 하고, 걸르기를
 * 빠뜨리면 어제 끝난 FOMC 가 계속 경고를 띄운다.
 *
 * <p>{@code stale} 이 참이면 <b>이 목록을 믿으면 안 된다.</b> 낡은 일정표의 증상은 오류가
 * 아니라 침묵이다 — 비어 보이는 것과 "다가오는 큰 일이 없다" 가 구별되지 않는다.
 */
@Schema(description = "예정 이벤트 목록", example = WatchApiExamples.CALENDAR_RESPONSE)
public record EventCalendarResponse(

        @Schema(description = "이 목록을 계산한 시각", example = "2026-08-23T12:00:00Z")
        Instant now,

        @Schema(description = "일정표가 낡았는가. 참이면 사람이 스냅샷을 갱신해야 한다",
                example = "false")
        boolean stale,

        @Schema(description = "아직 오지 않은 이벤트. 가까운 순서다")
        List<ScheduledEventResponse> events) {

    public EventCalendarResponse {
        events = List.copyOf(events);
    }

    static EventCalendarResponse from(EventCalendar calendar, Instant now) {
        return new EventCalendarResponse(
                now,
                calendar.isStale(now),
                calendar.upcoming(now).stream()
                        .map(event -> ScheduledEventResponse.from(event, now))
                        .toList());
    }
}
