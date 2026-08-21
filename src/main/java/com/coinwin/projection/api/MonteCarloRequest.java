package com.coinwin.projection.api;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.projection.domain.ProjectionSpec;
import io.swagger.v3.oas.annotations.media.Schema;

/** 같은 조건을 여러 번 돌려 결과 분포를 얻기 위한 요청. */
@Schema(description = "몬테카를로 시뮬레이션 요청", example = ProjectionApiExamples.MONTE_CARLO_REQUEST)
public record MonteCarloRequest(

        @Schema(description = "시뮬레이션 조건")
        ProjectionSpecRequest spec,

        @Schema(description = "시행 횟수. 1 이상 10000 이하", example = "1000")
        Integer runs,

        @Schema(description = "난수 시드. 같은 시드는 항상 같은 분포를 만든다", example = "20260821")
        Long seed) {

    ProjectionSpec toSpec() {
        return DomainValues.required(spec, "시뮬레이션 조건").toSpec();
    }

    int runsValue() {
        return DomainValues.required(runs, "시행 횟수");
    }

    long seedValue() {
        return DomainValues.required(seed, "시드");
    }
}
