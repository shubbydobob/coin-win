package com.coinwin.readout.api;

import com.coinwin.indicator.domain.BandRatio;
import com.coinwin.readout.domain.BollingerReadout;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * 볼린저가 지금 말하는 것.
 *
 * <p><b>「밴드 안」 은 너무 많은 것을 같은 사실로 만든다.</b> 하단에 붙어 있는 것과 상단 바로
 * 아래인 것이 같은 딱지를 받는데, 되돌림을 보는 사람에게 그 둘은 정반대 자리다.
 */
@Schema(description = "볼린저 판독. 밴드 안 어디인지까지 말하고 방향은 말하지 않는다")
public record BollingerReadoutResponse(

        @Schema(description = "밴드 대비 위치", example = "INSIDE",
                allowableValues = {"ABOVE", "INSIDE", "BELOW"})
        String position,

        @Schema(description = "밴드 상단", example = "80120.00")
        BigDecimal upper,

        @Schema(description = "밴드 중심. 20봉 단순이동평균이다", example = "78900.00")
        BigDecimal middle,

        @Schema(description = "밴드 하단", example = "77680.00")
        BigDecimal lower,

        @Schema(description = """
                밴드 폭 (%). **좁으면 변동성이 죽어 있다는 뜻**이고 그 자체로 방향을 뜻하지
                않는다 — 좁아진 뒤 어느 쪽으로 터지는가는 이 수가 답하지 않는다.""",
                example = "3.0900")
        BigDecimal bandWidthPercent,

        @Schema(description = """
                밴드 안에서 어디쯤인가. 하단이 0, 상단이 1 이고 **밖으로 나가면 그 범위를
                벗어난다** — 0~1 로 자르면 상단에 닿은 것과 뚫고 나간 것이 같은 값이 된다.

                **없을 수 있다** — 폭이 0 이면 "어디쯤" 이라는 물음이 성립하지 않는다.
                0 이나 0.5 로 채우지 않는다.""",
                example = "0.7412", nullable = true)
        BigDecimal ratio) {

    static BollingerReadoutResponse from(BollingerReadout readout) {
        return new BollingerReadoutResponse(
                readout.position().name(),
                readout.upper().value(),
                readout.middle().value(),
                readout.lower().value(),
                readout.bandWidthPercent().value(),
                readout.ratio().map(BandRatio::value).orElse(null));
    }
}
