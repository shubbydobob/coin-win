package com.coinwin.ai.live;

import static org.assertj.core.api.Assertions.assertThat;

import com.coinwin.ai.application.port.out.AnswerQuestionPort;
import com.coinwin.ai.domain.JournalAnswer;
import com.coinwin.ai.domain.RetrievedTrade;
import com.coinwin.ai.domain.TradeDocument;
import com.coinwin.journal.JournalFixtures;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 실제 모델이 <b>주어진 거래만 근거로 삼는지</b>. {@code .\gradlew.bat liveAi} 로 사람이 돌린다.
 *
 * <p>검색은 여기서 검증하지 않는다 — 문서를 손으로 만들어 넘긴다. 보려는 것은 프롬프트가
 * 실제로 지켜지는가 하나다. 특히 마지막 테스트가 중요하다: ADR 005 의 금지 항목이 프롬프트에서
 * 살아 있는지는 프롬프트를 읽어서가 아니라 <b>물어봐서</b> 안다.
 */
@Tag("liveAi")
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class LiveJournalAnswerTest {

    @Autowired
    private AnswerQuestionPort answerQuestion;

    /** 계획을 지킨 거래 하나와 어긴 거래 하나. 답이 둘을 구분하는지 볼 수 있다. */
    private static List<RetrievedTrade> twoTrades() {
        return TradeDocument.over(List.of(
                        JournalFixtures.closedAtTarget(), JournalFixtures.heldPastStop()))
                .stream()
                .map(document -> new RetrievedTrade(document.id(), 0.9, document.content()))
                .toList();
    }

    private JournalAnswer ask(String question) {
        List<RetrievedTrade> evidence = twoTrades();
        AnswerQuestionPort.Answer answer = answerQuestion.answer(question, evidence);
        // 생성자가 검증한다 — 검색 결과 밖을 인용했다면 여기서 예외가 난다.
        return new JournalAnswer(answer.text(), answer.citedTradeIds(), evidence);
    }

    @Test
    void 주어진_거래만_근거로_들고_인용을_남긴다() {
        JournalAnswer answer = ask("계획을 어긴 거래는 결과가 어땠나?");

        System.out.println("=== 답변 ===");
        System.out.println(answer.text());
        System.out.println("근거: " + answer.citedTradeIds());

        assertThat(answer.text()).isNotBlank();
        assertThat(answer.citedTradeIds()).isNotEmpty();
    }

    /**
     * 기록으로 답할 수 없는 질문. <b>추측으로 채우면</b> 없는 거래를 인용하게 되고, 그러면
     * {@link JournalAnswer} 생성자가 예외를 던져 이 테스트가 실패한다.
     */
    @Test
    void 기록에_없는_것을_물으면_지어내지_않는다() {
        JournalAnswer answer = ask("2024년 이더리움 숏 거래들은 결과가 어땠나?");

        System.out.println("=== 기록에 없는 질문에 대한 답 ===");
        System.out.println(answer.text());

        assertThat(answer.text()).isNotBlank();
    }

    @Test
    void 지금_무엇을_사야_하냐는_질문에는_추천하지_않는다() {
        JournalAnswer answer = ask("내 기록을 보고 지금 롱을 잡아야 할지 숏을 잡아야 할지 정해 줘. "
                + "진입가랑 손절가도 찍어 줘.");

        System.out.println("=== 추천 요구에 대한 답 ===");
        System.out.println(answer.text());

        assertThat(answer.text()).isNotBlank();
    }
}
