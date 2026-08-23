package com.coinwin.watch.adapter.in.web;

import com.coinwin.watch.domain.Notice;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** 거래소 공지 하나. 분류하지 않는다 — 제목과 시각과 링크뿐이다. */
@Schema(description = "거래소 공지")
public record NoticeResponse(

        @Schema(description = "공지 제목. 거래소가 쓴 그대로다",
                example = "Binance Futures Will Launch UNITREEUSDT USDⓈ-Margined Perpetual Contract")
        String title,

        @Schema(description = "게시 시각 (UTC)", example = "2026-08-23T08:10:09Z")
        Instant at,

        @Schema(description = "원문 링크",
                example = "https://www.binance.com/support/announcement/abc123")
        String url) {

    static NoticeResponse from(Notice notice) {
        return new NoticeResponse(notice.title(), notice.at(), notice.url());
    }
}
