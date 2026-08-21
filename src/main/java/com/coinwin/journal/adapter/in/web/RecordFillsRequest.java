package com.coinwin.journal.adapter.in.web;

import com.coinwin.common.domain.InvalidValueException;
import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import com.coinwin.indicator.domain.BandPosition;
import com.coinwin.journal.domain.ExecutedEntries;
import com.coinwin.journal.domain.Fill;
import com.coinwin.journal.domain.MarketContext;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 실제 체결 내역과 진입 시점의 시장 상태.
 *
 * <p>둘을 한 요청으로 받는 이유는 <b>진입 맥락이 이 순간에만 존재하기</b> 때문이다. 나중에
 * 따로 적게 두면 결과를 아는 채로 쓰게 되고, 그런 근거는 사후 분석에 쓸 수 없다.
 */
@Schema(description = "진입 체결과 그때의 시장 상태", example = JournalApiExamples.FILLS_REQUEST)
public record RecordFillsRequest(

        @Schema(description = "체결 내역. 시간 오름차순이어야 한다")
        List<FillRequest> fills,

        @Schema(description = "진입 시점의 시장 상태")
        MarketContextRequest context) {

    public RecordFillsRequest {
        fills = fills == null ? List.of() : List.copyOf(fills);
    }

    ExecutedEntries toEntries() {
        if (fills.isEmpty()) {
            throw new InvalidValueException("진입 체결 내역은(는) 최소 1건이어야 한다");
        }
        return new ExecutedEntries(fills.stream().map(FillRequest::toFill).toList());
    }

    MarketContext toContext() {
        if (context == null) {
            throw new InvalidValueException("진입 시점 시장 상태는(는) null 일 수 없다");
        }
        return context.toContext();
    }

    /** 체결 한 건. 계획과 달리 비중이 아니라 <b>수량</b>이다 — 계획대로 채워지지 않기 때문이다. */
    @Schema(description = "체결 한 건")
    public record FillRequest(

            @Schema(description = "실제 체결가. 계획한 지정가와 다를 수 있다", example = "60000")
            BigDecimal price,

            @Schema(description = "체결 수량 (BTC)", example = "0.05")
            BigDecimal quantity,

            @Schema(description = "체결 시각 (UTC)", example = "2026-08-01T01:00:00Z")
            Instant at) {

        Fill toFill() {
            return new Fill(Price.of(price), Quantity.of(quantity), at);
        }
    }

    /** 진입 시점의 시장 상태. 지지·저항은 구조화하지 않고 근거 문장으로 받는다. */
    @Schema(description = "진입 시점의 시장 상태")
    public record MarketContextRequest(

            @Schema(description = "진입 판단을 내린 시점의 가격", example = "60000")
            BigDecimal priceAtEntry,

            @Schema(description = "일목 구름 대비 위치", example = "ABOVE")
            BandPosition ichimokuPosition,

            @Schema(description = "볼린저 밴드 대비 위치", example = "INSIDE")
            BandPosition bollingerPosition,

            @Schema(description = "왜 들어갔는가. 비워 둘 수 없다 — 근거 없는 기록은 분석에 쓸 수 없다",
                    example = "4h 59,000 지지 3회 확인")
            String rationale) {

        MarketContext toContext() {
            return new MarketContext(Price.of(priceAtEntry),
                    ichimokuPosition, bollingerPosition, rationale);
        }
    }
}
