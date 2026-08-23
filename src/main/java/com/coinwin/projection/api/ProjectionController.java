package com.coinwin.projection.api;

import com.coinwin.market.application.port.in.LoadExchangeRateUseCase;
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
 * <p>묻는 방향이 둘이다. 앞의 둘은 규칙을 주면 <b>결과가 어떻게 갈리는지</b>를 내고,
 * {@code /compound-target} 은 결과를 정해 놓고 <b>거래 한 건에 무엇이 필요한지</b>를 낸다.
 * 뒤쪽은 지는 거래를 세지 않으므로 최선의 경우에 필요한 최소치만 말한다.
 *
 * <p>조율할 것이 없으면 application 계층을 만들지 않는다 — architecture.md 가 projection 을
 * 계층형으로 둔 이유가 이것이다. 그래서 도메인을 부르는 일은 이 컨트롤러가 직접 한다.
 *
 * <p><b>환율만 밖에서 온다.</b> {@code market} 의 인바운드 포트를 쓰는 것은
 * {@code position.application → market.application.port.in} 과 같은 판단이다 — "환율을
 * 어디서 얻는가" 는 {@code market} 의 정책이고, 여기서 어댑터를 직접 들면 그 정책이
 * {@code projection} 으로 샌다. 원화는 <b>곁들임</b>이라 못 얻어도 계산은 그대로 나간다.
 */
@RestController
@RequestMapping("/api/projections")
@Tag(name = "복리 시뮬레이션", description = "같은 규칙을 반복했을 때 자산이 지나가는 경로와 그 분포")
public class ProjectionController {

    private final LoadExchangeRateUseCase exchangeRate;

    public ProjectionController(LoadExchangeRateUseCase exchangeRate) {
        this.exchangeRate = exchangeRate;
    }

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
            summary = "월 목표 수익률을 지키려면 거래 한 건이 무엇을 해야 하는가",
            description = """
                    목표를 정해 놓고 거꾸로 푼다. 월 목표를 월 거래 수로 쪼개고,
                    거기에 레버리지가 키운 수수료·슬리피지를 더해 필요한 가격 변동을 낸다.

                    지는 거래를 세지 않는다. 모든 거래가 목표대로 끝난다는 가정 위의
                    산수이므로, 나온 수는 최선의 경우에 필요한 최소치다.""")
    @ApiResponses({
        @ApiResponse(responseCode = "200",
                description = "월말마다의 자산과, 거래 한 건에 요구되는 수익·비용·가격 변동"),
        @ApiResponse(responseCode = "400",
                description = "값 자체가 부적절하다. 0 이하의 목표·자산·레버리지, 누락된 필드"),
        @ApiResponse(responseCode = "422",
                description = "값은 유효하나 조건으로 성립하지 않는다. 총 거래 수 상한 초과, "
                        + "반올림해서 0 이 된 거래당 필요 수익")
    })
    @PostMapping("/compound-target")
    public CompoundTargetResponse compoundTarget(@RequestBody CompoundTargetRequest request) {
        return CompoundTargetResponse.from(request.toTarget(), exchangeRate.wonPerUsdt());
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
