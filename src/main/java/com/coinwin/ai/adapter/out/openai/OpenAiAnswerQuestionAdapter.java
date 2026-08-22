package com.coinwin.ai.adapter.out.openai;

import com.coinwin.ai.application.port.out.AnswerQuestionPort;
import com.coinwin.ai.domain.RetrievedTrade;
import com.coinwin.common.domain.ExternalDataUnavailableException;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * 검색된 거래만 보여 주고 답을 받는다.
 *
 * <p>모델이 보는 것은 질문과 검색된 문서뿐이다. 전체 기록을 넘기지 않으므로 <b>검색되지 않은
 * 거래를 인용하는 것은 지어내는 것</b>이고, 그것은 {@code JournalAnswer} 가 걸러 낸다.
 */
@Component
@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "openai", matchIfMissing = true)
class OpenAiAnswerQuestionAdapter implements AnswerQuestionPort {

    private final ChatClient chatClient;

    OpenAiAnswerQuestionAdapter(
            ChatClient.Builder builder,
            @Value("classpath:prompts/journal-answer.st") Resource instructions) {
        this.chatClient = builder.defaultSystem(instructions).build();
    }

    @Override
    public Answer answer(String question, List<RetrievedTrade> evidence) {
        ModelAnswer response = ask(question, evidence);
        return new Answer(response.answer(),
                response.citedTradeIds() == null ? List.of() : response.citedTradeIds());
    }

    private ModelAnswer ask(String question, List<RetrievedTrade> evidence) {
        ModelAnswer response;
        try {
            response = chatClient.prompt()
                    .user("질문: %s%n%n거래 기록:%n%s".formatted(question, rendered(evidence)))
                    .call()
                    .entity(ModelAnswer.class);
        } catch (RuntimeException failure) {
            throw new ExternalDataUnavailableException("답변을 받아 오지 못했다", failure);
        }
        if (response == null || response.answer() == null) {
            throw new ExternalDataUnavailableException("모델이 아무 답도 내놓지 않았다");
        }
        return response;
    }

    private static String rendered(List<RetrievedTrade> evidence) {
        return evidence.stream()
                .map(trade -> "[%s] %s".formatted(trade.tradeId(), trade.content()))
                .collect(Collectors.joining("\n"));
    }

    /** 모델이 돌려주는 JSON 의 모양. 검증 전의 날것이다. */
    record ModelAnswer(String answer, List<String> citedTradeIds) {
    }
}
