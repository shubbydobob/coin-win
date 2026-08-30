package com.coinwin.readout.api;

import com.coinwin.readout.domain.IchimokuReadout;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * 일목이 지금 말하는 것.
 *
 * <p><b>구름 위치 하나로 줄이지 않는다.</b> 「구름 위」 는 아슬아슬하게 위인지 한참 위인지,
 * 구름이 두꺼운지 종잇장인지를 전부 같은 사실로 만든다.
 *
 * <p><b>거리는 ATR 배수다.</b> 같은 300 도 조용한 장에서는 큰 값이고 급한 장에서는 아무것도
 * 아니다. 대의 폭과 손절 버퍼가 이미 이 단위로 정해져 있다.
 */
@Schema(description = "일목 판독. 위치와 값과 거리까지이며 방향은 말하지 않는다")
public record IchimokuReadoutResponse(

        @Schema(description = "구름 대비 위치", example = "ABOVE",
                allowableValues = {"ABOVE", "INSIDE", "BELOW"})
        String position,

        @Schema(description = "전환선 (9)", example = "79100.00")
        BigDecimal conversionLine,

        @Schema(description = "기준선 (26)", example = "78420.00")
        BigDecimal baseLine,

        @Schema(description = "구름 위 모서리. 두 선행스팬 중 큰 쪽이다", example = "78900.00")
        BigDecimal cloudTop,

        @Schema(description = "구름 아래 모서리", example = "77300.00")
        BigDecimal cloudBottom,

        @Schema(description = """
                선행스팬 1 이 2 위인가. **뒤집히는 것 자체가 전환으로 읽히는 자리다** —
                다만 그 읽기가 맞는지는 이 저장소가 재 본 적이 없다.""",
                example = "true")
        boolean bullishCloud,

        @Schema(description = """
                구름 두께를 ATR 로 잰 값. 언제나 0 이상이다. **두꺼우면 안 뚫린다는 뜻이
                아니다** — 두께는 과거 52봉의 폭이고 앞을 말하지 않는다.""",
                example = "1.35")
        BigDecimal cloudThickness,

        @Schema(description = """
                전환선 − 기준선을 ATR 로 잰 값. **부호가 절반이다** — 양수면 전환선이 위다.""",
                example = "0.42")
        BigDecimal conversionGap,

        @Schema(description = "종가 − 기준선을 ATR 로 잰 값. 기준선에서 얼마나 떨어져 있나",
                example = "1.10")
        BigDecimal baseLineGap) {

    static IchimokuReadoutResponse from(IchimokuReadout readout) {
        return new IchimokuReadoutResponse(
                readout.position().name(),
                readout.conversionLine().value(),
                readout.baseLine().value(),
                readout.cloudTop().value(),
                readout.cloudBottom().value(),
                readout.bullishCloud(),
                readout.cloudThickness().value(),
                readout.conversionGap().value(),
                readout.baseLineGap().value());
    }
}
