package com.coinwin.ai.application.service;

import com.coinwin.ai.application.AiPorts;
import com.coinwin.ai.application.port.in.AskJournalUseCase;
import com.coinwin.ai.application.port.out.AnswerQuestionPort;
import com.coinwin.ai.application.port.out.SearchTradesPort;
import com.coinwin.ai.domain.JournalAnswer;
import com.coinwin.ai.domain.RetrievedTrade;
import com.coinwin.common.domain.InvalidValueException;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 기록에서 찾고, 찾은 것만 근거로 답한다.
 *
 * <p>검색 결과가 비면 <b>모델을 부르지 않는다.</b> 없는 것에 대해 문장을 만들 기회 자체를 주지
 * 않는 것이 환각을 막는 가장 확실한 방법이고, 토큰도 쓰지 않는다.
 */
@Service
public class JournalQaService implements AskJournalUseCase {

    /** 넘기면 근거가 많아지는 것이 아니라 관련 없는 거래가 섞인다. */
    private static final int MAX_TOP_K = 20;

    private final Optional<SearchTradesPort> searchTrades;

    private final Optional<AnswerQuestionPort> answerQuestion;

    public JournalQaService(Optional<SearchTradesPort> searchTrades,
            Optional<AnswerQuestionPort> answerQuestion) {
        this.searchTrades = searchTrades;
        this.answerQuestion = answerQuestion;
    }

    @Override
    public JournalAnswer ask(String question, int topK) {
        List<RetrievedTrade> evidence =
                AiPorts.configured(searchTrades).search(requireText(question), requireTopK(topK));
        if (evidence.isEmpty()) {
            return JournalAnswer.nothingFound();
        }
        AnswerQuestionPort.Answer answer =
                AiPorts.configured(answerQuestion).answer(question.strip(), evidence);
        return new JournalAnswer(answer.text(), answer.citedTradeIds(), evidence);
    }

    private static String requireText(String question) {
        if (question == null || question.isBlank()) {
            throw new InvalidValueException("질문은(는) 비어 있을 수 없다");
        }
        return question.strip();
    }

    private static int requireTopK(int topK) {
        if (topK < 1 || topK > MAX_TOP_K) {
            throw new InvalidValueException(
                    "근거로 볼 거래 수는 1 이상 %d 이하여야 한다: %d".formatted(MAX_TOP_K, topK));
        }
        return topK;
    }
}
