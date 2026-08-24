package com.coinwin.readout.api;

import com.coinwin.readout.domain.VolumeShelfReadout;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * 매물대 하나.
 *
 * <p><b>대와 같은 모양이다.</b> 다른 것은 근거뿐이다 — 대는 몇 번 되돌아섰나(터치), 매물대는
 * 거기서 얼마나 오갔나(비중). 모양을 맞춘 이유는 화면이 둘을 나란히 놓기 때문이고, 둘이 같은
 * 자리를 가리키면 그것이 그 자리에 대한 두 개의 증거다.
 */
@Schema(description = "거래량이 몰린 가격 구간")
public record VolumeShelfResponse(

        @Schema(description = "지금 가격에 먼저 닿는 모서리", example = "77650.00")
        BigDecimal near,

        @Schema(description = """
                반대쪽 모서리. **이 구간을 지나려면 여기까지 가야 한다** —
                매물대는 점이 아니라 물린 물량이 쌓인 폭이다.""",
                example = "77200.00")
        BigDecimal far,

        @Schema(description = """
                전체 거래량의 몇 %가 이 구간에서 오갔나. **두께를 기간과 무관하게 견주는 수다** —
                BTC 수량은 보는 기간이 길수록 커져서 그 자체로는 두꺼운지 알 수 없다.""",
                example = "9.2400")
        BigDecimal sharePercent,

        @Schema(description = "지금 가격에서 가까운 모서리까지 몇 %. 언제나 0 이상이다",
                example = "1.1200")
        BigDecimal distancePercent) {

    static VolumeShelfResponse from(VolumeShelfReadout readout) {
        return new VolumeShelfResponse(
                readout.near().value(),
                readout.far().value(),
                readout.share().value(),
                readout.distancePercent().value());
    }
}
