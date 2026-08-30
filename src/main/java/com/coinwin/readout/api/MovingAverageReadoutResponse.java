package com.coinwin.readout.api;

import com.coinwin.indicator.domain.AtrMultiple;
import com.coinwin.readout.domain.MovingAverageReadout;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * 이동평균이 지금 말하는 것.
 *
 * <p><b>「정배열」 은 순서일 뿐이다.</b> 20 이 200 보다 1 위인 것과 다섯 배 벌어진 것이 같은
 * 딱지를 받는다.
 */
@Schema(description = "이동평균 판독. 순서가 아니라 벌어진 정도를 낸다")
public record MovingAverageReadoutResponse(

        @Schema(description = """
                (20 − 200) 을 ATR 로 잰 값. 부호가 어느 쪽이 위인지를 말한다.

                **없을 수 있다** — 200 구간은 봉 200 개가 있어야 값을 낸다. 0 으로 채우면
                "두 선이 붙어 있다" 는 없는 사실이 생긴다.""",
                example = "2.40", nullable = true)
        BigDecimal spread) {

    static MovingAverageReadoutResponse from(MovingAverageReadout readout) {
        return new MovingAverageReadoutResponse(
                readout.spread().map(AtrMultiple::value).orElse(null));
    }
}
