package com.coinwin.ai.application.port.out;

import com.coinwin.ai.domain.RetrievedTrade;
import java.util.List;

/**
 * 검색된 거래만 근거로 질문에 답한다.
 *
 * <p>돌려주는 것은 문장과 <b>인용한 식별자</b>다. 인용이 검색 결과 안에 있는지는
 * {@code JournalAnswer} 가 판정한다 — 어댑터가 판정까지 하면 제공자마다 기준이 갈린다.
 */
public interface AnswerQuestionPort {

    Answer answer(String question, List<RetrievedTrade> evidence);

    /** 모델이 내놓은 그대로. 검증 전이다. */
    record Answer(String text, List<String> citedTradeIds) {

        public Answer {
            citedTradeIds = List.copyOf(citedTradeIds);
        }
    }
}
