package com.coinwin.ai.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * 키가 없으면 Spring AI 자동 구성이 통째로 꺼지는지.
 *
 * <p>이 클래스가 없으면 앱이 <b>뜨지도 않는다.</b> OpenAI 스타터가 올리는 음성·이미지 모델은
 * 자격 증명 없이 빈 생성 단계에서 예외를 던진다. "AI 와 무관한 작업이 키를 요구하지 않는다"
 * 는 결정({@code docs/spec/phase7-spring-ai.md} § 8)이 실제로 성립하는지는 여기서 갈린다.
 */
class SpringAiEnabledOnlyWithApiKeyTest {

    private final SpringAiEnabledOnlyWithApiKey processor = new SpringAiEnabledOnlyWithApiKey();

    @Test
    void 키가_비어_있으면_채팅과_임베딩과_벡터스토어를_전부_끈다() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty(SpringAiEnabledOnlyWithApiKey.API_KEY, "");

        processor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.ai.model.chat")).isEqualTo("none");
        assertThat(environment.getProperty("spring.ai.model.embedding")).isEqualTo("none");
        assertThat(environment.getProperty("spring.ai.vectorstore.type")).isEqualTo("none");
    }

    @Test
    void 키가_아예_없어도_끈다() {
        MockEnvironment environment = new MockEnvironment();

        processor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.ai.model.chat")).isEqualTo("none");
    }

    /**
     * 공백만 있는 값은 키가 아니다. {@code OPENAI_API_KEY=" "} 를 키로 인정하면 빈이 올라오고,
     * 그 다음 실패는 기동 시점이 아니라 <b>사용자가 요청을 보낸 뒤</b> 401 로 나타난다.
     */
    @Test
    void 공백뿐인_키는_키가_아니다() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty(SpringAiEnabledOnlyWithApiKey.API_KEY, "   ");

        processor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.ai.model.chat")).isEqualTo("none");
    }

    @Test
    void 키가_있으면_채팅과_임베딩과_벡터스토어를_켠_채로_둔다() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty(SpringAiEnabledOnlyWithApiKey.API_KEY, "sk-test");

        processor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.ai.model.chat")).isNull();
        assertThat(environment.getProperty("spring.ai.model.embedding")).isNull();
        assertThat(environment.getProperty("spring.ai.vectorstore.type")).isNull();
    }

    /**
     * 음성·이미지·모더레이션은 키가 있어도 쓰지 않는다. 이 셋이 켜져 있으면 키가 없을 때
     * <b>빈 생성 단계에서 기동이 실패한다</b> — 실제로 그렇게 깨졌고, 그래서 이 테스트가 있다.
     */
    @Test
    void 쓰지_않는_모델은_키가_있든_없든_끈다() {
        MockEnvironment withKey = new MockEnvironment()
                .withProperty(SpringAiEnabledOnlyWithApiKey.API_KEY, "sk-test");
        MockEnvironment withoutKey = new MockEnvironment();

        processor.postProcessEnvironment(withKey, null);
        processor.postProcessEnvironment(withoutKey, null);

        for (MockEnvironment environment : new MockEnvironment[] {withKey, withoutKey}) {
            assertThat(environment.getProperty("spring.ai.model.audio.speech")).isEqualTo("none");
            assertThat(environment.getProperty("spring.ai.model.audio.transcription"))
                    .isEqualTo("none");
            assertThat(environment.getProperty("spring.ai.model.image")).isEqualTo("none");
            assertThat(environment.getProperty("spring.ai.model.moderation")).isEqualTo("none");
        }
    }
}
