package com.coinwin.ai.adapter.out.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.ai.domain.DraftedFields;
import com.coinwin.ai.domain.PlanField;
import com.coinwin.ai.domain.PlanNotUnderstoodException;
import com.coinwin.common.domain.Price;
import com.coinwin.position.domain.Direction;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.core.io.ByteArrayResource;

/**
 * 어댑터의 번역 계약. <b>네트워크도 키도 없다</b> — 고정 응답을 내는 모델을 물린다.
 *
 * <p>여기서 보는 것은 "모델이 이런 모양으로 답했을 때 우리가 무엇을 얻는가" 다. 모델이 규칙을
 * 어겼을 때 <b>지어내지 않고 못 읽은 것으로 두는지</b>가 핵심이다.
 */
class OpenAiExtractPlanAdapterTest {

    private static final String COMPLETE_JSON = """
            {
              "direction": "LONG",
              "entries": [
                {"price": 62000, "allocation": 50},
                {"price": 60000, "allocation": 50}
              ],
              "stopLoss": 58000,
              "takeProfit": 68000,
              "leverage": 10
            }""";

    private static ChatModel modelSaying(String text) {
        return prompt -> new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    private static DraftedFields extractWith(String modelAnswer) {
        OpenAiExtractPlanAdapter adapter = new OpenAiExtractPlanAdapter(
                org.springframework.ai.chat.client.ChatClient.builder(modelSaying(modelAnswer)),
                new ByteArrayResource("계획을 옮겨 적는다".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        return adapter.extractFrom("아무 문장");
    }

    @Test
    void 모델이_돌려준_JSON_을_도메인_칸으로_옮긴다() {
        DraftedFields fields = extractWith(COMPLETE_JSON);

        assertThat(fields.missing()).isEmpty();
        assertThat(fields.direction()).isEqualTo(Direction.LONG);
        assertThat(fields.stopLoss()).isEqualTo(Price.of("58000"));
        assertThat(fields.entries()).hasSize(2);
    }

    /** 모델은 종종 JSON 을 코드 펜스로 감싼다. 그것 때문에 계획이 깨지면 안 된다. */
    @Test
    void 마크다운_펜스로_감싼_JSON_도_읽어낸다() {
        DraftedFields fields = extractWith("```json\n" + COMPLETE_JSON + "\n```");

        assertThat(fields.missing()).isEmpty();
        assertThat(fields.direction()).isEqualTo(Direction.LONG);
    }

    /**
     * 프롬프트는 LONG / SHORT 만 쓰라고 했지만 모델은 규칙을 어길 수 있다. 그때 "롱" 을
     * LONG 으로 <b>고쳐 주지 않는다</b> — 고쳐 주기 시작하면 어디까지 고칠지가 어댑터마다 갈린다.
     */
    @Test
    void 방향을_규칙_밖의_말로_답하면_못_읽은_것으로_둔다() {
        DraftedFields fields = extractWith(COMPLETE_JSON.replace("\"LONG\"", "\"롱\""));

        assertThat(fields.missing()).containsExactly(PlanField.DIRECTION);
    }

    @Test
    void 대소문자와_공백은_받아_준다() {
        DraftedFields fields = extractWith(COMPLETE_JSON.replace("\"LONG\"", "\" long \""));

        assertThat(fields.direction()).isEqualTo(Direction.LONG);
    }

    @Test
    void 비워_둔_칸은_비워_둔_채로_넘어온다() {
        DraftedFields fields = extractWith(COMPLETE_JSON.replace("\"stopLoss\": 58000", "\"stopLoss\": null"));

        assertThat(fields.missing()).containsExactly(PlanField.STOP_LOSS);
    }

    @Test
    void JSON_이_아닌_답은_계획으로_읽어내지_못한_것이다() {
        assertThatThrownBy(() -> extractWith("글쎄요, 지금은 관망하시는 게 좋겠습니다."))
                .isInstanceOf(PlanNotUnderstoodException.class);
    }
}
