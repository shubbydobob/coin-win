package com.coinwin.ai.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 답변은 검색된 거래만 근거로 들 수 있다.
 *
 * <p>이 규칙이 없으면 모델이 그럴듯한 UUID 를 지어낸 답이 그대로 나간다. 사용자가 그 거래를
 * 찾지 못하는 순간부터 <b>맞는 답도 믿을 수 없게 된다.</b>
 */
class JournalAnswerTest {

    private static final RetrievedTrade FOUND =
            new RetrievedTrade("trade-1", 0.83, "롱 10배 거래. 계획을 지켰다.");

    @Test
    void 검색된_거래를_근거로_든_답은_성립한다() {
        JournalAnswer answer = new JournalAnswer(
                "손실 직후 들어간 거래 한 건이 있었다.", List.of("trade-1"), List.of(FOUND));

        assertThat(answer.citedTradeIds()).containsExactly("trade-1");
    }

    @Test
    void 검색되지_않은_거래를_인용하면_거절한다() {
        assertThatThrownBy(() -> new JournalAnswer(
                "trade-9 에서 크게 잃었다.", List.of("trade-9"), List.of(FOUND)))
                .isInstanceOf(UnknownCitationException.class)
                .hasMessageContaining("trade-9");
    }

    @Test
    void 근거를_하나도_들지_않은_답도_성립한다() {
        assertThatCode(() -> new JournalAnswer("판단할 만한 기록이 없다.", List.of(), List.of(FOUND)))
                .doesNotThrowAnyException();
    }

    /** 검색 결과가 없으면 모델을 부르지 않는다. 없는 것에 대해 문장을 만들 기회를 주지 않는다. */
    @Test
    void 검색_결과가_없을_때의_답은_모델_없이_만들어진다() {
        JournalAnswer answer = JournalAnswer.nothingFound();

        assertThat(answer.text()).contains("기록이 없다");
        assertThat(answer.citedTradeIds()).isEmpty();
        assertThat(answer.retrieved()).isEmpty();
    }

    @Test
    void 빈_답은_답이_아니다() {
        assertThatThrownBy(() -> new JournalAnswer("  ", List.of(), List.of(FOUND)))
                .isInstanceOf(com.coinwin.common.domain.InvalidValueException.class);
    }
}
