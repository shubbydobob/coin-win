package com.coinwin.readout.api;

import com.coinwin.readout.domain.RsiReadout;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * RSI 가 지금 말하는 것.
 *
 * <p><b>50 으로 접히던 자리다.</b> 「50 위」 는 51 과 78 을 같은 사실로 만든다.
 */
@Schema(description = "RSI 판독. 50 으로 접기 전의 값을 낸다")
public record RsiReadoutResponse(

        @Schema(description = """
                지금 RSI. **50 · 70 · 30 같은 경계는 관습이고 이 저장소가 검증한 수가 아니다.**""",
                example = "62.4100")
        BigDecimal value,

        @Schema(description = """
                3봉 전 대비 변화(%p). **비율이 아니라 비율의 차라 음수가 될 수 있다.**
                3봉이라는 수는 임의로 고른 것이고 검증한 적이 없다.""",
                example = "-4.1200")
        BigDecimal change3) {

    static RsiReadoutResponse from(RsiReadout readout) {
        return new RsiReadoutResponse(readout.value().value(), readout.change3());
    }
}
