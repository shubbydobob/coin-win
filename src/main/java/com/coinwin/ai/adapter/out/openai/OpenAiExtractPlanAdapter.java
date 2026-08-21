package com.coinwin.ai.adapter.out.openai;

import com.coinwin.ai.application.port.out.ExtractPlanPort;
import com.coinwin.ai.domain.DraftedFields;
import com.coinwin.ai.domain.PlanNotUnderstoodException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * 문장에서 계획의 칸을 읽어 온다.
 *
 * <p>조건이 Spring AI 자신의 조건과 <b>같은 모양</b>인 것은 의도된 것이다. 채팅 자동 구성이
 * 꺼지면({@code SpringAiEnabledOnlyWithApiKey}) {@code ChatClient.Builder} 빈이 없고,
 * 그때 이 어댑터가 살아 있으면 주입 실패로 컨텍스트가 통째로 깨진다. 같은 스위치를 보게 해서
 * 둘의 생사를 묶는다.
 *
 * <p>여기서 하는 일은 번역뿐이다 — 모델의 답을 도메인 어휘로 옮긴다. 빠진 칸을 어떻게 할지는
 * {@link DraftedFields} 가 정한다.
 */
@Component
@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "openai", matchIfMissing = true)
class OpenAiExtractPlanAdapter implements ExtractPlanPort {

    private final ChatClient chatClient;

    OpenAiExtractPlanAdapter(
            ChatClient.Builder builder,
            @Value("classpath:prompts/plan-draft.st") Resource instructions) {
        this.chatClient = builder.defaultSystem(instructions).build();
    }

    @Override
    public DraftedFields extractFrom(String sentence) {
        return ask(sentence).toFields();
    }

    /**
     * {@code validateSchema} 는 모델이 형식을 어겼을 때 스키마를 붙여 한 번 더 묻는다.
     * 값을 고치는 것이 아니라 <b>모양</b>을 고치는 것이므로 "추측해서 채우지 않는다" 와
     * 어긋나지 않는다. 비어 있는 칸은 비어 있는 채로 돌아온다.
     */
    private DraftedPlanResponse ask(String sentence) {
        DraftedPlanResponse response;
        try {
            response = chatClient.prompt()
                    .user(sentence)
                    .call()
                    .entity(DraftedPlanResponse.class, ChatClient.EntityParamSpec::validateSchema);
        } catch (RuntimeException failure) {
            throw new PlanNotUnderstoodException("모델의 답을 계획으로 읽어내지 못했다", failure);
        }
        if (response == null) {
            throw new PlanNotUnderstoodException("모델이 아무 답도 내놓지 않았다", null);
        }
        return response;
    }
}
