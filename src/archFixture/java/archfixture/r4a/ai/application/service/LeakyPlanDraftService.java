package archfixture.r4a.ai.application.service;

import archfixture.r4a.ai.adapter.out.openai.OpenAiExtractPlanAdapter;

/**
 * 규칙 4 위반 — <b>ai 쪽</b>: ai.application 이 ai.adapter 를 직접 참조한다.
 *
 * <p>{@code market}(r4) · {@code journal}(r4j) 과 따로 두는 이유는 같다. 규칙 4 는 모듈
 * 이름을 <b>손으로 적어</b> 열거하므로, 모듈마다 픽스처가 없으면 항목이 빠지거나 오타가 나도
 * 규칙은 계속 초록이다.
 *
 * <p>ai 에서 이 규칙이 특히 중요한 이유가 있다. 서비스가 어댑터를 직접 잡으면
 * {@code ChatClient} 가 application 으로 새고, 그 순간 <b>AI 없이 도는 테스트</b>라는 것이
 * 성립하지 않는다. 근거: {@code docs/spec/phase7-spring-ai.md} § 3
 */
public class LeakyPlanDraftService {
    private final OpenAiExtractPlanAdapter adapter = new OpenAiExtractPlanAdapter();

    public String draft() {
        return adapter.extract();
    }
}
