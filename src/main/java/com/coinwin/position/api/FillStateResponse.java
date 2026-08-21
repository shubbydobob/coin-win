package com.coinwin.position.api;

import com.coinwin.position.domain.FillState;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/** 분할 진입 중 특정 시점까지 체결됐을 때의 포지션 상태. */
@Schema(description = "n 건까지 체결됐을 때의 포지션 상태")
public record FillStateResponse(

        @Schema(description = "이 시점까지 체결된 진입 회차 수", example = "1")
        int filledEntries,

        @Schema(description = "체결된 회차만으로 다시 가중한 평균 진입가", example = "60000.00")
        BigDecimal averageEntryPrice,

        @Schema(description = "이 시점에 실제로 들고 있는 수량 (BTC)", example = "0.00266667")
        BigDecimal quantity,

        @Schema(description = "이 평단에서의 청산가. 분할 진입은 체결이 진행될수록 이 값이 움직인다",
                example = "54240.00")
        BigDecimal liquidationPrice,

        @Schema(description = "이 시점에 손절가에 닿았을 때의 손실 (USDT)", example = "10.67")
        BigDecimal maxLoss) {

    static FillStateResponse from(FillState state) {
        return new FillStateResponse(
                state.filledEntries(),
                state.averageEntryPrice().value(),
                state.quantity().value(),
                state.liquidationPrice().value(),
                state.maxLoss().value());
    }
}
