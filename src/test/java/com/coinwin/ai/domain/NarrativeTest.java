package com.coinwin.ai.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 요약이 원본에 없는 수를 쓰면 거절한다.
 *
 * <p>ADR 005 는 <b>"요약은 원본 수치와 대조할 수 있다"</b> 를 근거로 요약을 허용했다. 이 클래스가
 * 그 대조를 코드로 만든 것이다. 검사를 테스트에만 두면 실제 응답은 검증되지 않은 채 나가므로
 * 운영 경로에서도 돈다.
 */
class NarrativeTest {

    private static SummaryFacts facts() {
        Map<String, BigDecimal> numbers = new LinkedHashMap<>();
        numbers.put("거래 수", new BigDecimal("24"));
        numbers.put("승률", new BigDecimal("33.3300"));
        numbers.put("손익비", new BigDecimal("0.70"));
        numbers.put("순손익", new BigDecimal("-80.00"));
        numbers.put("최대낙폭", new BigDecimal("12.4567"));
        numbers.put("최종 자산", new BigDecimal("720.00"));
        numbers.put("평단", new BigDecimal("62000.00"));
        return new SummaryFacts(numbers, Map.of("구간", "2026-01-01 ~ 2026-06-01"));
    }

    private static Narrative narrate(String text) {
        return new Narrative(text, facts());
    }

    @Test
    void 원본에_있는_수만_쓴_요약은_통과한다() {
        Narrative narrative = narrate("24 번 거래해 승률 33.33%, 손익비 0.70 으로 720.00 이 남았다.");

        assertThat(narrative.text()).contains("승률");
    }

    @Test
    void 원본에_없는_가격을_쓰면_거절한다() {
        assertThatThrownBy(() -> narrate("평단 64000 에서 잡았다."))
                .isInstanceOf(FabricatedNumberException.class)
                .hasMessageContaining("64000");
    }

    /** 표시 형식은 자유다. 대조 기준은 글자가 아니라 값이다. */
    @Test
    void 자릿수_구분_쉼표와_소수점_표기가_달라도_같은_수로_본다() {
        assertThatCode(() -> narrate("평단은 62,000 이고 다시 쓰면 62000.00 이다."))
                .doesNotThrowAnyException();
    }

    /**
     * 요약이 수를 반올림해 쓰는 것은 지어내는 것이 아니다. 12.4567 을 "12.5" 로 쓰는 것까지
     * 막으면 읽을 수 있는 문장이 나오지 않는다.
     */
    @Test
    void 반올림해서_쓴_수는_통과한다() {
        assertThatCode(() -> narrate("최대낙폭은 12.5% 였고 승률은 33.3% 였다."))
                .doesNotThrowAnyException();
    }

    /** 반올림해도 원본이 되지 않는 수는 지어낸 것이다. */
    @Test
    void 반올림_범위를_벗어난_수는_거절한다() {
        assertThatThrownBy(() -> narrate("최대낙폭은 13.5% 였다."))
                .isInstanceOf(FabricatedNumberException.class);
    }

    /**
     * 1000 미만 정수는 검사하지 않는다. 가격으로 오인될 수 없고 문장 구성에 필요하다 —
     * "두 배", "세 가지", "상위 3개" 를 전부 막으면 요약을 쓸 수 없다.
     */
    @Test
    void 작은_정수는_문장_구성용으로_허용한다() {
        assertThatCode(() -> narrate("이유는 두 가지다. 3 번째 구간이 특히 나빴다."))
                .doesNotThrowAnyException();
    }

    /** 소수는 작아도 검사한다. 승률·손익비·낙폭이 전부 소수이기 때문이다. */
    @Test
    void 작아도_소수면_검사한다() {
        assertThatThrownBy(() -> narrate("손익비는 1.42 였다."))
                .isInstanceOf(FabricatedNumberException.class);
    }

    /** ISO 날짜의 연도를 지어낸 가격으로 읽으면 구간을 언급하는 순간 모든 요약이 거절된다. */
    @Test
    void 날짜_안의_숫자는_지어낸_수로_보지_않는다() {
        assertThatCode(() -> narrate("2026-01-01 부터 2026-06-01 까지의 결과다."))
                .doesNotThrowAnyException();
    }

    @Test
    void 지어낸_수가_여럿이면_전부_알려_준다() {
        assertThatThrownBy(() -> narrate("평단 64000 에서 잡아 71000 에 닫았다."))
                .isInstanceOf(FabricatedNumberException.class)
                .hasMessageContaining("64000")
                .hasMessageContaining("71000");
    }

    @Test
    void 빈_요약은_요약이_아니다() {
        assertThatThrownBy(() -> narrate("   "))
                .isInstanceOf(com.coinwin.common.domain.InvalidValueException.class);
    }

    /**
     * 모델이 보는 것이 이것뿐이다. <b>맥락이 먼저, 수치가 나중</b>이고 순서는 넣은 순서를
     * 유지한다 — 프롬프트에 넣는 순서가 곧 읽는 순서다.
     */
    @Test
    void 프롬프트에_넣을_모양은_맥락과_수치를_순서대로_늘어놓는다() {
        String rendered = facts().rendered();

        assertThat(rendered.lines().toList()).containsExactly(
                "- 구간: 2026-01-01 ~ 2026-06-01",
                "- 거래 수: 24",
                "- 승률: 33.3300",
                "- 손익비: 0.70",
                "- 순손익: -80.00",
                "- 최대낙폭: 12.4567",
                "- 최종 자산: 720.00",
                "- 평단: 62000.00");
    }

    @Test
    void 음수도_원본과_대조한다() {
        assertThatCode(() -> narrate("순손익은 -80.00 이다.")).doesNotThrowAnyException();
        assertThatThrownBy(() -> narrate("순손익은 -1080.00 이다."))
                .isInstanceOf(FabricatedNumberException.class);
    }
}
