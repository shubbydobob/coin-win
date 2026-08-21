package com.coinwin.journal.adapter.in.web;

import com.coinwin.indicator.domain.BandPosition;
import com.coinwin.journal.domain.ExecutedEntries;
import com.coinwin.journal.domain.MarketContext;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * 실제로 어떻게 들어갔는가. 계획 상태의 거래에는 없다({@code null}).
 *
 * <p>평단과 수량은 저장된 값이 아니라 체결 내역에서 계산된 값이다. 저장해 두면 체결 내역과
 * 어긋날 수 있는 두 번째 사본이 생긴다.
 */
@Schema(description = "실제 진입 결과와 그때의 시장 상태")
public record EntryResultResponse(

        @Schema(description = "포지션이 열린 시각. 첫 체결이다", example = "2026-08-01T01:00:00Z")
        Instant openedAt,

        @Schema(description = "체결 회차 수. 계획보다 적으면 분할이 다 채워지지 않은 것이다",
                example = "2")
        int fillCount,

        @Schema(description = "수량 가중 평균 진입가. 계획 평단과의 차이가 슬리피지다",
                example = "59500.00")
        BigDecimal averageEntryPrice,

        @Schema(description = "총 체결 수량 (BTC)", example = "0.10000000")
        BigDecimal quantity,

        @Schema(description = "진입 판단 시점의 가격", example = "60000.00")
        BigDecimal priceAtEntry,

        @Schema(description = "일목 구름 대비 위치", example = "ABOVE")
        BandPosition ichimokuPosition,

        @Schema(description = "볼린저 밴드 대비 위치", example = "INSIDE")
        BandPosition bollingerPosition,

        @Schema(description = "진입 근거", example = "4h 59,000 지지 3회 확인")
        String rationale) {

    static EntryResultResponse from(ExecutedEntries entries, MarketContext context) {
        return new EntryResultResponse(
                entries.firstFilledAt(),
                entries.count(),
                entries.averagePrice().value(),
                entries.totalQuantity().value(),
                context.priceAtEntry().value(),
                context.ichimokuPosition(),
                context.bollingerPosition(),
                context.rationale());
    }
}
