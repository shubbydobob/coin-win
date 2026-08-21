package com.coinwin.ai.config;

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

/**
 * {@code OPENAI_API_KEY} 가 없으면 Spring AI 자동 구성을 끈다.
 *
 * <p><b>이것이 없으면 앱이 기동하지 않는다.</b> OpenAI 스타터의 자동 구성은 키가 없어도
 * 모델 빈을 만들려 하고, 그 안의 SDK 가 "At least one credential source must be specified"
 * 로 빈 생성 단계에서 예외를 던진다. 포지션 계산도 백테스트도 매매 기록도 AI 와 무관한데
 * 키가 없다는 이유로 전부 못 쓰게 되는 것은 결정과 반대다
 * ({@code docs/spec/phase7-spring-ai.md} § 8).
 *
 * <p>끄는 방법이 <b>속성 세 개</b>인 이유는 스프링 AI 의 조건이 그렇게 생겼기 때문이다.
 * 각 자동 구성은 {@code havingValue} 가 제공자 이름이고 {@code matchIfMissing = true} 인
 * 조건을 달고 있어, 값이 없으면 켜지고 다른 값이면 꺼진다. 그래서 끄는 값은 무엇이든 되지만
 * {@code none} 으로 통일한다.
 *
 * <p>음성·이미지·모더레이션은 <b>키와 무관하게 항상</b> 끈다. 매매 도구에 음성 합성 빈이
 * 있을 이유가 없다. 이것을 {@code application.yml} 이 아니라 여기에 둔 이유는
 * <b>테스트 리소스의 {@code application.yml} 이 메인 것을 통째로 가리기 때문</b>이다.
 * 설정 파일에 두면 기동을 증명하는 바로 그 테스트가 그 설정을 보지 못한다.
 *
 * <p>{@link Order} 가 가장 낮은 우선순위인 것은 {@code application.yml} 이 먼저 읽혀야 하기
 * 때문이다. 설정 파일을 읽는 것도 같은 종류의 후처리기이고, 그보다 먼저 돌면 키가 환경변수로
 * 직접 주어진 경우 말고는 언제나 "키가 없다" 로 판정한다.
 */
@Order(Ordered.LOWEST_PRECEDENCE)
public class SpringAiEnabledOnlyWithApiKey implements EnvironmentPostProcessor {

    static final String API_KEY = "spring.ai.openai.api-key";

    static final String PROPERTY_SOURCE = "coinwin-ai-disabled";

    /** 쓰지 않으므로 언제나 끈다. */
    private static final Map<String, Object> NEVER_USED = Map.of(
            "spring.ai.model.audio.speech", "none",
            "spring.ai.model.audio.transcription", "none",
            "spring.ai.model.image", "none",
            "spring.ai.model.moderation", "none");

    /** 쓰지만 키가 있어야 쓸 수 있는 것. */
    private static final Map<String, Object> NEEDS_KEY = Map.of(
            "spring.ai.model.chat", "none",
            "spring.ai.model.embedding", "none",
            "spring.ai.vectorstore.type", "none");

    @Override
    public void postProcessEnvironment(
            ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> disabled = new HashMap<>(NEVER_USED);
        if (!StringUtils.hasText(environment.getProperty(API_KEY))) {
            disabled.putAll(NEEDS_KEY);
        }
        // addLast 다. 이 값들은 <b>기본값이지 강제가 아니다</b> — 명시적으로 켠 설정이 있으면
        // 그쪽이 이긴다. addFirst 로 두면 통합 테스트가 가짜 임베딩 빈을 주고 벡터 스토어를
        // 켜려 해도 켤 방법이 없다. 실제로 그렇게 막혔고, 그래서 바꿨다.
        environment.getPropertySources()
                .addLast(new MapPropertySource(PROPERTY_SOURCE, disabled));
    }
}
