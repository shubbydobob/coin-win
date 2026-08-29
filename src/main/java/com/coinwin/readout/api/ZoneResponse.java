package com.coinwin.readout.api;

import com.coinwin.readout.domain.ZoneReadout;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * 지금 가격에서 가장 가까운 대 하나.
 *
 * <p><b>대에는 폭이 있다.</b> 한 값으로 내면 "76,500 이 지지" 처럼 읽히는데 실제로는 구간이고,
 * 그 폭이 손절을 어디 둘지를 가른다. 그래서 두 모서리를 다 낸다.
 */
@Schema(description = "가장 가까운 지지 또는 저항 구간")
public record ZoneResponse(

        @Schema(description = """
                지금 가격에 가까운 쪽 모서리. **먼저 닿는 값이라 이쪽이 판단의 기준이다** —
                지지대는 위쪽 모서리, 저항대는 아래쪽 모서리가 여기 온다.""",
                example = "76500.00")
        BigDecimal near,

        @Schema(description = """
                반대쪽 모서리. 대를 뚫었는지는 여기까지 가 봐야 안다 —
                가까운 모서리를 스친 것과 대를 통과한 것은 다른 사실이다.""",
                example = "76120.00")
        BigDecimal far,

        @Schema(description = """
                이 구간에 몇 번 닿았나. **많을수록 사람이 실제로 반응한 자리다.**
                최소 2회부터 대로 친다 — 한 번 닿은 것은 대가 아니라 그냥 지나간 가격이다.""",
                example = "3")
        int touches,

        @Schema(description = """
                지금 가격에서 가까운 모서리까지 몇 %. **언제나 0 이상이다** —
                위인지 아래인지는 이 값이 지지에 붙었는지 저항에 붙었는지가 이미 말한다.""",
                example = "0.4210")
        BigDecimal distancePercent) {

    static ZoneResponse from(ZoneReadout readout) {
        return new ZoneResponse(
                readout.near().value(),
                readout.far().value(),
                readout.touches(),
                readout.distancePercent().value());
    }
}
