package com.coinwin.position.api;

import com.coinwin.position.application.PositionAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 진입 전 계획의 리스크를 산출한다. 저장하지 않는다 — Phase 1 에는 DB 가 없다.
 *
 * <p>주문을 내지 않는다. 이 프로젝트는 계산과 기록이 목적이고, 주문 실행은 scope.md 가
 * 명시적으로 제외한 범위다.
 */
@RestController
@RequestMapping("/api/position-plans")
@Tag(name = "포지션 계획", description = "분할 진입 계획의 총 리스크를 진입 전에 산출한다")
public class PositionPlanController {

    private final PositionAnalysisService positionAnalysisService;

    public PositionPlanController(PositionAnalysisService positionAnalysisService) {
        this.positionAnalysisService = positionAnalysisService;
    }

    @Operation(
            summary = "분할 진입 계획의 체결 상태별 리스크 산출",
            description = """
                    1건 체결부터 전량 체결까지 각 시점의 평단·수량·청산가·최대손실을 낸다.
                    수량은 요청에 없다 — 손절가와 리스크 비율이 수량을 결정하기 때문이다.""")
    @ApiResponses({
        @ApiResponse(responseCode = "200",
                description = "체결 상태별 평단 · 수량 · 청산가 · 최대손실, 그리고 필요 증거금과 손익비"),
        @ApiResponse(responseCode = "400",
                description = "값 자체가 부적절하다. 음수 가격, 누락된 필드, 0 이하의 잔고"),
        @ApiResponse(responseCode = "422",
                description = "값은 유효하나 계획으로 성립하지 않는다. 손절가 방향 오류, "
                        + "비중 합 불일치, 손절가가 청산가 너머")
    })
    @PostMapping("/analysis")
    public PositionAnalysisResponse analyze(@RequestBody AnalyzePositionRequest request) {
        return PositionAnalysisResponse.from(
                positionAnalysisService.analyze(request.toPlan(), request.toBudget()));
    }
}
