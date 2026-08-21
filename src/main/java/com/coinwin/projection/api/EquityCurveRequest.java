package com.coinwin.projection.api;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.projection.domain.ProjectionSpec;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 표본 경로 하나를 그리기 위한 요청.
 *
 * <p>시드를 서버가 정하지 않고 요청이 명시한다. 같은 시드가 같은 곡선을 내야 어제 본 경로를
 * 오늘 다시 불러올 수 있고, 그래야 두 조건을 같은 운 위에서 비교할 수 있다.
 */
@Schema(description = "표본 경로 요청", example = ProjectionApiExamples.CURVE_REQUEST)
public record EquityCurveRequest(

        @Schema(description = "시뮬레이션 조건")
        ProjectionSpecRequest spec,

        @Schema(description = "난수 시드. 같은 시드는 항상 같은 승패 순서를 만든다", example = "20260821")
        Long seed) {

    ProjectionSpec toSpec() {
        return DomainValues.required(spec, "시뮬레이션 조건").toSpec();
    }

    long seedValue() {
        return DomainValues.required(seed, "시드");
    }
}
