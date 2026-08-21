package com.coinwin.ai.adapter.in.web;

import com.coinwin.ai.domain.JournalAnswer;
import com.coinwin.ai.domain.RetrievedTrade;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 답변과 그 근거.
 *
 * <p>{@code retrieved} 를 함께 내려 주는 것이 이 기능의 안전장치다. 답변만 돌려주면 사용자는
 * 그 문장을 믿는 수밖에 없다. 근거로 쓰인 거래를 나란히 두면 원본 기록을 직접 열어 확인할 수
 * 있고, <b>그 대조 가능성이 ADR 005 가 RAG 를 허용한 근거</b>다.
 */
@Schema(description = "매매 기록 질의 답변. 인용된 거래는 반드시 검색 결과 안에 있다")
public record JournalAnswerResponse(

        @Schema(description = "한국어 답변")
        String answer,

        @Schema(description = "답변이 근거로 든 거래 식별자")
        List<String> citedTradeIds,

        @Schema(description = "질문에 걸린 거래들. 인용되지 않은 것도 포함한다")
        List<RetrievedTradeResponse> retrieved) {

    public JournalAnswerResponse {
        citedTradeIds = List.copyOf(citedTradeIds);
        retrieved = List.copyOf(retrieved);
    }

    @Schema(description = "검색된 거래 하나")
    public record RetrievedTradeResponse(

            @Schema(description = "거래 식별자. 이 값으로 원본 기록을 열 수 있다")
            String tradeId,

            @Schema(description = "유사도. 어댑터마다 계산이 다르므로 순서를 읽는 데만 쓴다")
            double score,

            @Schema(description = "색인된 문장. 모델이 본 것도 이것이다")
            String summary) {

        static RetrievedTradeResponse from(RetrievedTrade trade) {
            return new RetrievedTradeResponse(trade.tradeId(), trade.score(), trade.content());
        }
    }

    public static JournalAnswerResponse from(JournalAnswer answer) {
        return new JournalAnswerResponse(answer.text(), answer.citedTradeIds(),
                answer.retrieved().stream().map(RetrievedTradeResponse::from).toList());
    }
}
