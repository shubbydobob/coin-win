package com.coinwin.ai.adapter.in.web;

import com.coinwin.ai.application.port.in.DraftPlanUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 자연어를 구조로 바꾸는 엔드포인트.
 *
 * <p><b>여기서 나온 것은 무엇도 자동으로 실행되지 않는다.</b> 저장도 주문도 계산도 하지 않고
 * 초안만 돌려준다. 매수·매도 시점 추천과 가격 예측은 요청받아도 하지 않는다 —
 * 근거는 {@code docs/adr/005}.
 */
@RestController
@RequestMapping("/api/ai")
@Tag(name = "AI 보조", description = "자연어를 구조로 바꾸고 수치를 문장으로 바꾼다. 판단하지 않는다")
public class AiController {

    private final DraftPlanUseCase draftPlan;

    public AiController(DraftPlanUseCase draftPlan) {
        this.draftPlan = draftPlan;
    }

    @Operation(
            summary = "자연어 → 매매 계획 초안",
            description = """
                    문장에 있는 값만 옮겨 적는다. 없는 값은 추측해서 채우지 않고
                    무엇이 없는지 되물어 온다 — 잘못 채운 진입가는 잘못된 포지션 사이즈로
                    이어지고 그 오류는 계산 계층 전체를 조용히 오염시킨다.

                    총수량은 초안에 없다. 수량은 손절가와 리스크 예산이 결정하는 값이므로
                    /api/position-plans/analysis 가 계산한다.""")
    @ApiResponses({
        @ApiResponse(responseCode = "400", description = "문장이 비어 있다"),
        @ApiResponse(responseCode = "422",
                description = "문장에서 읽어내지 못한 항목이 있거나, 읽어낸 값이 계획으로 "
                        + "성립하지 않는다. 응답 본문이 무엇이 빠졌는지 알려 준다"),
        @ApiResponse(responseCode = "503",
                description = "AI 기능이 설정되지 않았다. OPENAI_API_KEY 가 필요하다")
    })
    @PostMapping("/plan-draft")
    public PlanDraftResponse draftPlan(@RequestBody PlanDraftRequest request) {
        return PlanDraftResponse.from(draftPlan.draftFrom(request.text()));
    }
}
