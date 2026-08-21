package com.coinwin.ai.adapter.out.openai;

import com.coinwin.ai.application.port.out.WriteSummaryPort;
import com.coinwin.ai.domain.SummaryFacts;
import com.coinwin.common.domain.ExternalDataUnavailableException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * 수치 목록을 문장으로 바꾼다.
 *
 * <p><b>모델에는 수치만 간다.</b> 캔들도 거래 목록도 넘기지 않는다 — 토큰과 비용의 문제이기도
 * 하지만, 요약이 원본 수치 밖으로 나갈 여지를 없애는 쪽이 더 중요하다. 모델이 보지 못한 수는
 * 지어낼 수도 없다.
 *
 * <p>{@code ChatClient.Builder} 빈은 프로토타입 스코프라 어댑터마다 다른 시스템 프롬프트를
 * 걸어도 서로를 덮어쓰지 않는다.
 */
@Component
@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "openai", matchIfMissing = true)
class OpenAiWriteSummaryAdapter implements WriteSummaryPort {

    private final ChatClient chatClient;

    OpenAiWriteSummaryAdapter(
            ChatClient.Builder builder,
            @Value("classpath:prompts/backtest-summary.st") Resource instructions) {
        this.chatClient = builder.defaultSystem(instructions).build();
    }

    @Override
    public String write(SummaryFacts facts) {
        String written;
        try {
            written = chatClient.prompt().user(facts.rendered()).call().content();
        } catch (RuntimeException failure) {
            throw new ExternalDataUnavailableException("요약을 받아 오지 못했다", failure);
        }
        if (written == null) {
            throw new ExternalDataUnavailableException("모델이 아무 답도 내놓지 않았다");
        }
        return written;
    }
}
