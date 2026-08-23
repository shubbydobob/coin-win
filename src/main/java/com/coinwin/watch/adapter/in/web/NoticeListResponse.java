package com.coinwin.watch.adapter.in.web;

import com.coinwin.watch.domain.Notice;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * 최근 공지 목록. 최근 것부터다.
 *
 * <p><b>매크로 뉴스가 아니다.</b> 상장·상장폐지·점검 같은 거래소 자체 소식이고, 재무부 발표
 * 같은 것은 여기 걸리지 않는다. 그쪽은 {@link EventCalendarResponse} 가 담당한다.
 */
@Schema(description = "최근 거래소 공지")
public record NoticeListResponse(

        @Schema(description = "이 목록을 받은 시각", example = "2026-08-23T12:00:00Z")
        Instant at,

        @Schema(description = "공지. 최근 것부터")
        List<NoticeResponse> notices) {

    public NoticeListResponse {
        notices = List.copyOf(notices);
    }

    static NoticeListResponse from(List<Notice> notices, Instant at) {
        return new NoticeListResponse(at, notices.stream().map(NoticeResponse::from).toList());
    }
}
