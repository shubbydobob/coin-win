package com.coinwin.readout.api;

import com.coinwin.readout.domain.MacdReadout;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * MACD 가 지금 말하는 것.
 *
 * <p><b>부호 하나로 접히던 자리다.</b> 「시그널 위」 는 방금 넘어온 것과 스무 봉째 위에 있는
 * 것을 같은 사실로 만든다.
 */
@Schema(description = "MACD 판독. 부호와 크기와 이어진 길이를 함께 낸다")
public record MacdReadoutResponse(

        @Schema(description = """
                히스토그램을 ATR 로 잰 값. **가격의 차라 그대로 두면 시대가 비교되지 않는다** —
                60,000 시절의 100 과 100,000 시절의 100 은 다른 크기다.""",
                example = "0.31")
        BigDecimal histogram,

        @Schema(description = "직전 봉 대비 히스토그램 변화. 붙는 중인지 벌어지는 중인지를 말한다",
                example = "-0.04")
        BigDecimal change,

        @Schema(description = """
                MACD 선이 영선 위인가. **시그널 대비와 다른 사실이다** — 시그널 위이면서
                영선 아래인 자리가 있고, 그것을 한 부호로 접으면 둘이 구별되지 않는다.""",
                example = "true")
        boolean aboveZero,

        @Schema(description = """
                지금 부호가 **이어진 봉 수**. 양수면 시그널 위, 음수면 아래이고
                0 이면 히스토그램이 정확히 0 이다.""",
                example = "7")
        int barsSinceCross) {

    static MacdReadoutResponse from(MacdReadout readout) {
        return new MacdReadoutResponse(
                readout.histogram().value(),
                readout.change().value(),
                readout.aboveZero(),
                readout.barsSinceCross());
    }
}
