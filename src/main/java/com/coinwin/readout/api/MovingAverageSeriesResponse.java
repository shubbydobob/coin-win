package com.coinwin.readout.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 이동평균선 하나.
 *
 * <p><b>구간이 값 안에 있다.</b> 응답을 {@code {"20": [...], "50": [...]}} 처럼 지도로 내면
 * 생성된 타입에서 키가 문자열이 되고, 화면이 그 문자열을 다시 숫자로 바꿔야 한다.
 */
@Schema(description = "이동평균선 하나")
public record MovingAverageSeriesResponse(
        @Schema(description = "구간(봉 수)", example = "20")
        int period,
        @Schema(description = "봉마다의 값. **봉이 모자라면 빈 목록이다**")
        List<PricePointResponse> points) {

    public MovingAverageSeriesResponse {
        points = List.copyOf(points);
    }
}
