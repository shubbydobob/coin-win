package com.coinwin.market.adapter.in.web;

import com.coinwin.market.domain.MacroQuote;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 거시 자산 다섯. 나스닥 · 금 · 원유 · 국채 · 변동성.
 *
 * <p><b>못 읽은 것은 목록에서 빠진다.</b> 다섯이 셋으로 오는 것이 정상적인 실패 모양이고,
 * 빠진 이유는 서버 로그에 남는다. 한 종목의 실패로 이 블록 전체를 죽이지 않는다.
 */
@Schema(description = "거시 자산 시세 목록")
public record MacroQuoteListResponse(

        @Schema(description = "위험자산 · 안전자산 · 원자재 · 금리 · 공포 순이다")
        List<MacroQuoteResponse> quotes) {

    public MacroQuoteListResponse {
        quotes = List.copyOf(quotes);
    }

    static MacroQuoteListResponse from(List<MacroQuote> quotes) {
        return new MacroQuoteListResponse(MacroQuoteResponse.from(quotes));
    }
}
