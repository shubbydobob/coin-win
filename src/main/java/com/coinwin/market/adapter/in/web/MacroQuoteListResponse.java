package com.coinwin.market.adapter.in.web;

import com.coinwin.market.domain.MacroQuote;
import com.coinwin.market.domain.MacroWatchlist;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 거시 자산. 주가 · 금속 · 에너지 · 국채 · 공포.
 *
 * <p><b>못 읽은 것은 목록에서 빠진다.</b> 열둘이 아홉으로 오는 것이 정상적인 실패 모양이고,
 * 빠진 이유는 서버 로그에 남는다. 한 종목의 실패로 이 블록 전체를 죽이지 않는다.
 *
 * <p><b>{@code requested} 를 함께 낸다.</b> 화면이 "몇 개를 못 읽었나" 를 세려면 물어본 수가
 * 있어야 하는데, 그것을 화면에 상수로 두면 관심 목록이 늘어난 날 조용히 거짓말이 된다 —
 * 실제로 다섯을 상수로 박아 두었고 열둘로 늘리는 순간 그 자리가 틀렸다.
 */
@Schema(description = "거시 자산 시세 목록")
public record MacroQuoteListResponse(

        @Schema(description = "주가 · 금속 · 에너지 · 국채 · 공포 순이다")
        List<MacroQuoteResponse> quotes,

        @Schema(description = """
                물어본 종목 수. quotes 보다 크면 그 차이만큼 못 읽은 것이다 —
                화면이 이 수를 스스로 알면 관심 목록이 늘어난 날 거짓말이 된다.""",
                example = "12")
        int requested) {

    public MacroQuoteListResponse {
        quotes = List.copyOf(quotes);
    }

    static MacroQuoteListResponse from(List<MacroQuote> quotes) {
        return new MacroQuoteListResponse(
                MacroQuoteResponse.from(quotes), MacroWatchlist.size());
    }
}
