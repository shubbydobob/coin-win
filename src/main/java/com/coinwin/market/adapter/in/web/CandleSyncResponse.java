package com.coinwin.market.adapter.in.web;

import com.coinwin.market.domain.CandleQuery;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 증분 수집 결과.
 *
 * <p>{@code newlyStored} 가 "받은 수" 가 아니라 "새로 저장된 수" 인 것이 핵심이다. 같은
 * 구간을 두 번 수집하면 두 번째는 0 이 나와야 하고, 그것이 Phase 3 완료 조건
 * "캔들 증분 저장에 중복 없음" 을 API 에서 그대로 확인하는 방법이다.
 */
@Schema(description = "거래소에서 받아 저장한 결과", example = MarketApiExamples.SYNC_RESPONSE)
public record CandleSyncResponse(

        @Schema(description = "종목", example = "BTCUSDT")
        String symbol,

        @Schema(description = "캔들 주기", example = "1h")
        String interval,

        @Schema(description = "새로 저장된 캔들 수. 이미 다 있었다면 0 이다", example = "24")
        int newlyStored) {

    static CandleSyncResponse from(CandleQuery query, int newlyStored) {
        return new CandleSyncResponse(
                query.symbol().value(), query.interval().code(), newlyStored);
    }
}
