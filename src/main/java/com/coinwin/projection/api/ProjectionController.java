package com.coinwin.projection.api;

import com.coinwin.projection.domain.MonteCarloProjection;
import com.coinwin.projection.domain.ProjectionSpec;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 승률·손익비·리스크 비율을 반복했을 때 자산이 어떻게 움직이는지 계산한다.
 *
 * <p>예측이 아니다. 입력한 가정이 그대로 유지된다면 산술적으로 무엇이 따라 나오는지를
 * 보여줄 뿐이고, 승률과 손익비를 대는 것은 사람이다.
 *
 * <p>주입받는 것이 없어 생성자가 없다. 조율할 것이 없으면 application 계층도 만들지
 * 않는다 — architecture.md 가 projection 을 계층형으로 둔 이유가 이것이다.
 */
@RestController
@RequestMapping("/api/projections")
@Tag(name = "복리 시뮬레이션", description = "같은 규칙을 반복했을 때 자산이 지나가는 경로와 그 분포")
public class ProjectionController {

    @Operation(
            summary = "시드 하나가 만드는 표본 자산 곡선",
            description = """
                    승패 순서를 시드로 뽑아 자산 곡선 하나를 그린다.
                    같은 시드는 항상 같은 곡선을 낸다.""")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "거래마다 한 점인 자산 곡선. 첫 점이 초기 자본이다"),
        @ApiResponse(responseCode = "400",
                description = "값 자체가 부적절하다. 음수 자본, 100% 를 넘는 승률, 누락된 필드"),
        @ApiResponse(responseCode = "422",
                description = "값은 유효하나 조건으로 성립하지 않는다. 총 거래 수 상한 초과")
    })
    @PostMapping("/equity-curve")
    public EquityCurveResponse equityCurve(@RequestBody EquityCurveRequest request) {
        return EquityCurveResponse.from(request.toSpec().simulate(request.seedValue()));
    }

    @Operation(
            summary = "같은 조건 N 회 반복의 결과 분포",
            description = """
                    기댓값이 같아도 경로에 따라 결과가 갈린다. 하위 5% 와 상위 5% 의 간격,
                    그리고 최대낙폭 분포가 그 차이를 수치로 보여준다.""")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "백분위 다섯 점과 손실 확률, 최대낙폭 분포"),
        @ApiResponse(responseCode = "400",
                description = "값 자체가 부적절하다. 0 이하의 시행 횟수, 누락된 필드"),
        @ApiResponse(responseCode = "422",
                description = "값은 유효하나 조건으로 성립하지 않는다. 시행 횟수나 총 거래 수 상한 초과")
    })
    @PostMapping("/monte-carlo")
    public MonteCarloResponse monteCarlo(@RequestBody MonteCarloRequest request) {
        ProjectionSpec spec = request.toSpec();
        return MonteCarloResponse.from(
                new MonteCarloProjection(spec, request.runsValue(), request.seedValue()).run(),
                spec);
    }
}
