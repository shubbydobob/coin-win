package com.coinwin.market.adapter.in.web;

import com.coinwin.market.domain.PriceLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

/** 호가 한 단 — 이 가격에 이만큼 걸려 있다. */
@Schema(description = "호가 한 단")
public record PriceLevelResponse(

        @Schema(description = "호가", example = "76567.40")
        BigDecimal price,

        @Schema(description = "그 가격에 걸린 수량 (BTC)", example = "24.77800000")
        BigDecimal quantity) {

    static List<PriceLevelResponse> from(List<PriceLevel> levels) {
        return levels.stream()
                .map(level -> new PriceLevelResponse(
                        level.price().value(), level.quantity().value()))
                .toList();
    }
}
