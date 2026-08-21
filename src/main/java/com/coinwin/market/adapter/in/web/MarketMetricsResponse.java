package com.coinwin.market.adapter.in.web;

import com.coinwin.market.domain.MarketMetrics;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * 진입 판단에 쓰는 시장 상태 한 장.
 *
 * <p>세 값을 따로 내보내지 않는 이유는 시각을 하나로 묶기 위해서다. 각각 조회하면 서로 다른
 * 시각의 값을 나란히 놓고 판단하게 된다.
 */
@Schema(description = "한 시점의 펀딩비·미결제약정·롱숏비율",
        example = MarketApiExamples.METRICS_RESPONSE)
public record MarketMetricsResponse(

        @Schema(description = "종목", example = "BTCUSDT")
        String symbol,

        @Schema(description = "이 값들의 관측 시각", example = "2026-08-21T08:00:00Z")
        Instant at,

        @Schema(description = "펀딩비 (%). 음수면 숏이 롱에게 낸다", example = "0.010000")
        BigDecimal fundingRatePercent,

        @Schema(description = "미결제약정 (BTC). 늘면서 가격이 오르면 신규 롱이 들어온 것이다",
                example = "81234.50000000")
        BigDecimal openInterest,

        @Schema(description = "롱 계정 수 / 숏 계정 수. 금액이 아니라 계정 수 기준이다",
                example = "1.8342")
        BigDecimal longShortRatio) {

    static MarketMetricsResponse from(MarketMetrics metrics) {
        return new MarketMetricsResponse(
                metrics.symbol().value(),
                metrics.at(),
                metrics.fundingRate().value(),
                metrics.openInterest().value(),
                metrics.longShortRatio());
    }
}
