package com.coinwin.market.adapter.in.web;

import com.coinwin.market.domain.MacroQuote;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

/**
 * 비트코인 밖의 자산 하나.
 *
 * <p><b>상관관계를 계산하지 않는다.</b> "나스닥이 오르니 BTC 도 오른다" 는 예측이고
 * {@code scope.md} 가 금지한 것이다. 값과 변동률까지만 내고 읽는 것은 사람이다.
 */
@Schema(description = "거시 자산 시세")
public record MacroQuoteResponse(

        @Schema(description = "바이낸스 심볼", example = "QQQUSDT")
        String symbol,

        @Schema(description = """
                사람이 읽는 이름. **레버리지 상품은 배수를 이름에 적는다** —
                배수를 숨기면 '미 장기국채 +0.4%' 가 국채가 0.4% 움직였다는 뜻으로 읽히는데
                실제로는 그 3분의 1이다.""",
                example = "나스닥 100")
        String label,

        @Schema(description = """
                어느 묶음인가. 열두 종목을 한 줄로 늘어놓으면 목록이 되고 목록은 읽히지 않는다.""",
                example = "EQUITY",
                allowableValues = {"EQUITY", "METAL", "ENERGY", "RATES", "FEAR"})
        String group,

        @Schema(description = "묶음의 사람이 읽는 이름", example = "주가")
        String groupLabel,

        @Schema(description = "현재가 (USDT)", example = "612.34")
        BigDecimal last,

        @Schema(description = "24시간 변동률 (%). 음수면 하락이다", example = "0.840000")
        BigDecimal change24hPercent) {

    static List<MacroQuoteResponse> from(List<MacroQuote> quotes) {
        return quotes.stream()
                .map(quote -> new MacroQuoteResponse(
                        quote.symbol().value(),
                        quote.label(),
                        quote.group().name(),
                        quote.group().label(),
                        quote.last().value(),
                        quote.change24hPercent()))
                .toList();
    }
}
