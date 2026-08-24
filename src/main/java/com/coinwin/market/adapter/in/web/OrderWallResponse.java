package com.coinwin.market.adapter.in.web;

import com.coinwin.market.domain.OrderWall;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * 호가창에서 유난히 두꺼운 한 단 — 호가 매물대.
 *
 * <p><b>거래량 매물대와 다른 것이다.</b> 판독 화면의 매물대는 <b>이미 체결된</b> 물량이고
 * 이것은 <b>아직 체결되지 않은</b> 주문이다. 체결된 것은 사라지지 않지만 호가는 한순간에
 * 취소되므로, 이쪽이 훨씬 약한 증거다.
 */
@Schema(description = "평균보다 두꺼운 호가 한 단")
public record OrderWallResponse(

        @Schema(description = "그 단의 가격", example = "78700.00")
        BigDecimal price,

        @Schema(description = "그 단에 걸린 잔량 (BTC)", example = "12.40000000")
        BigDecimal quantity,

        @Schema(description = """
                같은 쪽 호가 한 단 평균의 몇 배인가. **배수로 말하는 이유는** 12 BTC 가 두꺼운지
                얇은지를 그 자체로는 알 수 없기 때문이다 — 이상치 지표가 분위로 말하는 것과 같다.""",
                example = "8.1400")
        BigDecimal multipleOfAverage) {

    static OrderWallResponse from(OrderWall wall) {
        return new OrderWallResponse(
                wall.level().price().value(),
                wall.level().quantity().value(),
                wall.multipleOfAverage());
    }
}
