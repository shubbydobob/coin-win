package com.coinwin.ai.adapter.in.web;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 계획을 적어 놓은 자연어 한 문단.
 *
 * @param text 진입가·비중·손절·익절·레버리지가 들어 있는 문장
 */
@Schema(description = "매매 계획을 적은 자연어 문장", example = AiApiExamples.PLAN_DRAFT_REQUEST)
public record PlanDraftRequest(

        @Schema(description = "계획을 적은 문장. 빠진 항목은 채워지지 않고 그대로 되물어 온다",
                example = "6만2천에 절반, 6만에 절반 롱. 손절 5만8천, 익절 6만8천, 10배")
        String text) {
}
