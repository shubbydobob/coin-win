package com.coinwin.ai.live;

import static org.assertj.core.api.Assertions.assertThat;

import com.coinwin.ai.application.port.in.SummarizeUseCase;
import com.coinwin.ai.domain.Narrative;
import com.coinwin.ai.domain.SummaryFacts;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 실제 모델이 <b>주어진 수만 쓰는지</b>. {@code .\gradlew.bat liveAi} 로 사람이 돌린다.
 *
 * <p>단언이 거의 없는 것처럼 보이지만 그렇지 않다. {@link Narrative} 생성자가 원본에 없는 수를
 * 거부하므로, <b>요약이 만들어졌다는 사실 자체가 대조를 통과했다는 뜻</b>이다.
 *
 * <p>수치는 Phase 6 이 실제 캔들에서 낸 표의 기본 조합이다(4시간봉 1,500봉, 손익비 0.70).
 * 요약이 이것을 성공으로 읽으면 프롬프트가 틀린 것이다 — 그래서 문장을 찍어 눈으로도 본다.
 */
@Tag("liveAi")
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class LiveSummaryTest {

    @Autowired
    private SummarizeUseCase summarize;

    private static SummaryFacts phase6Baseline() {
        Map<String, BigDecimal> numbers = new LinkedHashMap<>();
        numbers.put("거래 수", new BigDecimal("24"));
        numbers.put("승률(%)", new BigDecimal("33.3300"));
        numbers.put("손익비", new BigDecimal("0.70"));
        numbers.put("순손익(USDT)", new BigDecimal("-80.00"));
        numbers.put("최종 자산(USDT)", new BigDecimal("720.00"));
        numbers.put("최대낙폭(%)", new BigDecimal("12.5000"));
        numbers.put("초기 자본(USDT)", new BigDecimal("800.00"));
        numbers.put("손절 버퍼(ATR)", new BigDecimal("1.0"));
        numbers.put("군집 배수(ATR)", new BigDecimal("0.5"));
        Map<String, String> context = new LinkedHashMap<>();
        context.put("종목", "BTCUSDT");
        context.put("주기", "4h");
        context.put("구간", "2025-12-14 ~ 2026-08-21");
        return new SummaryFacts(numbers, context);
    }

    @Test
    void 요약은_원본에_없는_수를_쓰지_않는다() {
        Narrative narrative = summarize.summarize(phase6Baseline());

        System.out.println("=== 백테스트 요약 ===");
        System.out.println(narrative.text());

        assertThat(narrative.text()).isNotBlank();
    }

    /**
     * 사실을 하나만 주면 할 말이 거의 없다. 그럴 때 <b>빈칸을 채우려 드는지</b>가 드러난다 —
     * 모델이 그럴듯한 맥락을 지어내면 없는 수가 섞이고 대조에서 걸린다.
     */
    @Test
    void 사실이_빈약해도_빈칸을_지어내지_않는다() {
        Map<String, BigDecimal> numbers = new LinkedHashMap<>();
        numbers.put("거래 수", BigDecimal.ZERO);
        Narrative narrative = summarize.summarize(
                new SummaryFacts(numbers, Map.of("종목", "BTCUSDT")));

        System.out.println("=== 거래가 없는 요약 ===");
        System.out.println(narrative.text());

        assertThat(narrative.text()).isNotBlank();
    }
}
