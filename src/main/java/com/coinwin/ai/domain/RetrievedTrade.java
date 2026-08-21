package com.coinwin.ai.domain;

import com.coinwin.common.domain.DomainValues;

/**
 * 질문에 걸린 거래 하나. 답변의 근거가 될 수 있는 것은 여기 담긴 것뿐이다.
 *
 * @param tradeId 원본 거래 식별자. 사용자가 기록을 직접 열어 볼 수 있어야 한다
 * @param score 유사도. 어댑터마다 계산이 다르므로 <b>순서를 읽는 데만</b> 쓴다
 * @param content 색인된 문장. 모델에 넘어가는 것도, 화면에 보이는 것도 이것이다
 */
public record RetrievedTrade(String tradeId, double score, String content) {

    public RetrievedTrade {
        DomainValues.required(tradeId, "거래 식별자");
        DomainValues.required(content, "색인된 문장");
    }
}
