package com.coinwin.ai.adapter.in.web;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 매매 기록에 대한 질문.
 *
 * @param question 지난 매매에 대해 묻는 말. 앞으로의 매매를 묻는 질문에는 답하지 않는다
 * @param topK 근거로 볼 거래 수. 비우면 8
 */
@Schema(description = "매매 기록 질의", example = AiApiExamples.JOURNAL_QUERY_REQUEST)
public record JournalQueryRequest(

        @Schema(description = "지난 매매에 대한 질문",
                example = "손실 직후에 들어간 거래는 결과가 어땠나?")
        String question,

        @Schema(description = "근거로 볼 거래 수. 1~20, 비우면 8", example = "8")
        Integer topK) {

    /** 넘기면 근거가 늘어나는 것이 아니라 관련 없는 거래가 섞인다. */
    private static final int DEFAULT_TOP_K = 8;

    int effectiveTopK() {
        return topK == null ? DEFAULT_TOP_K : topK;
    }
}
