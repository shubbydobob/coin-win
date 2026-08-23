package com.coinwin.watch.adapter.in.web;

import com.coinwin.watch.application.port.in.LoadCalendarUseCase;
import com.coinwin.watch.application.port.in.LoadNoticesUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 무슨 일이 예정돼 있나.
 *
 * <p><b>공고와 캘린더를 다른 엔드포인트로 낸다.</b> 한 응답에 묶으면 공고가 죽을 때 캘린더도
 * 못 본다 — 공고 쪽은 문서화되지 않은 엔드포인트라 예고 없이 바뀔 수 있다.
 */
@RestController
@RequestMapping("/api/watch")
@Tag(name = "감시", description = "예정 이벤트와 거래소 공고")
public class WatchController {

    private final LoadCalendarUseCase loadCalendar;
    private final LoadNoticesUseCase loadNotices;
    private final Clock clock;

    public WatchController(
            LoadCalendarUseCase loadCalendar, LoadNoticesUseCase loadNotices, Clock clock) {
        this.loadCalendar = loadCalendar;
        this.loadNotices = loadNotices;
        this.clock = clock;
    }

    @Operation(
            summary = "예정 이벤트",
            description = """
                    FOMC · CPI · 재무부 자금조달계획까지 남은 시간.

                    경고(`warning`)는 **규모**에 대한 것이다 — "그날은 명목을 줄여라" 이지
                    "오를 것이다" 가 아니다. 방향은 이 프로젝트가 말하지 않는다.

                    `stale` 이 참이면 커밋된 일정표가 낡은 것이므로 이 목록을 믿으면 안 된다.""")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "아직 오지 않은 이벤트. 가까운 순서다"),
        @ApiResponse(responseCode = "503", description = "일정 스냅샷을 읽지 못했다")
    })
    @GetMapping("/events")
    public EventCalendarResponse events() {
        return EventCalendarResponse.from(loadCalendar.calendar(), clock.instant());
    }

    @Operation(
            summary = "거래소 공지",
            description = """
                    바이낸스가 최근에 낸 공지. 제목과 시각과 링크뿐이고 분류하지 않는다.

                    **매크로 뉴스가 아니다** — 상장 · 상장폐지 · 점검 같은 거래소 자체 소식이다.
                    재무부 발표 같은 것은 여기 걸리지 않고 `/events` 가 담당한다.""")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "최근 공지. 최근 것부터"),
        @ApiResponse(responseCode = "503", description = "거래소 공지 페이지에 닿지 못했다")
    })
    @GetMapping("/notices")
    public NoticeListResponse notices() {
        return NoticeListResponse.from(loadNotices.recent(), clock.instant());
    }
}
