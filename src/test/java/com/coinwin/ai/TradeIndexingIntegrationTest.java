package com.coinwin.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.coinwin.ai.application.port.in.AskJournalUseCase;
import com.coinwin.ai.application.port.out.AnswerQuestionPort;
import com.coinwin.ai.domain.JournalAnswer;
import com.coinwin.journal.JournalFixtures;
import com.coinwin.journal.application.port.in.RecordTradeUseCase;
import com.coinwin.common.domain.Quantity;
import com.coinwin.journal.domain.ExecutedEntries;
import com.coinwin.journal.domain.Exit;
import com.coinwin.journal.domain.Fill;
import com.coinwin.journal.domain.ExitReason;
import com.coinwin.journal.domain.TradeClosure;
import com.coinwin.journal.domain.TradeCosts;
import com.coinwin.journal.domain.TradeId;
import com.coinwin.common.domain.Price;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import com.coinwin.PostgresImage;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Phase 7 완료 조건 — <b>실제 매매 한 건이 청산되면 저절로 색인되고 질의에 잡힌다.</b>
 *
 * <p>앞의 계약 테스트가 어댑터 하나씩을 봤다면 이 테스트는 <b>조립</b>을 본다. 청산 이벤트가
 * 실제로 발행되는지, 듣는 쪽이 컨텍스트에 있는지, 색인이 진짜 PostgreSQL 에 들어가는지,
 * 검색이 그것을 다시 찾아내는지가 한 줄로 이어져야 한다. 어느 한 고리가 빠져도 개별 테스트는
 * 전부 초록일 수 있다.
 *
 * <p>임베딩은 결정론적 가짜이고 답변은 스텁이다 — <b>OPENAI_API_KEY 없이 돈다.</b> 여기서
 * 검증하려는 것은 모델의 답이 아니라 배선이다.
 */
@Tag("integration")
@SpringBootTest(properties = {
    "spring.flyway.enabled=true",
    // 키가 없으면 SpringAiEnabledOnlyWithApiKey 가 벡터 스토어를 끈다. 이 테스트는 가짜
    // 임베딩 빈을 직접 주므로 다시 켠다.
    "spring.ai.vectorstore.type=pgvector",
    "spring.ai.vectorstore.pgvector.initialize-schema=false"
})
@Import(TradeIndexingIntegrationTest.FakeAiBeans.class)
class TradeIndexingIntegrationTest {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            PostgresImage.current());

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void 컨테이너를_가리키게_한다(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access",
                () -> "true");
    }

    @Autowired
    private RecordTradeUseCase record;

    @Autowired
    private AskJournalUseCase ask;

    /**
     * 시각을 {@code now} 기준으로 만드는 이유는 계획 시각을 <b>애플리케이션의 시계</b>가
     * 찍기 때문이다. 고정된 픽스처 시각을 쓰면 "체결은 계획보다 앞설 수 없다" 는 규칙에 걸린다.
     */
    @Test
    void 청산한_거래가_저절로_색인되어_질의에_잡힌다() {
        Instant now = Instant.now();
        TradeId id = record.planTrade(JournalFixtures.longPlan()).id();
        record.recordFills(id, ExecutedEntries.of(
                new Fill(Price.of("60000"), Quantity.of("0.05"), now.plusSeconds(3600)),
                new Fill(Price.of("59000"), Quantity.of("0.05"), now.plusSeconds(7200))),
                JournalFixtures.context());

        record.closeTrade(id, new TradeClosure(
                new Exit(Price.of("64000"), now.plusSeconds(10_800)),
                ExitReason.PLANNED_TARGET,
                TradeCosts.of("5.00", "1.20")));

        JournalAnswer answer = ask.ask("계획을 지킨 롱 거래가 있었나?", 5);

        assertThat(answer.retrieved()).isNotEmpty();
        assertThat(answer.retrieved().getFirst().tradeId()).isEqualTo(id.value().toString());
        assertThat(answer.retrieved().getFirst().content()).contains("계획을 지켰다");
    }

    /** 모델 없이 도는 배선. 임베딩은 결정론적이고 답변은 첫 근거를 그대로 인용한다. */
    @TestConfiguration
    static class FakeAiBeans {

        @Bean
        EmbeddingModel embeddingModel() {
            return new DeterministicEmbeddingModel();
        }

        @Bean
        AnswerQuestionPort answerQuestionPort() {
            return (question, evidence) -> new AnswerQuestionPort.Answer(
                    "근거 %d 건을 찾았다.".formatted(evidence.size()),
                    List.of(evidence.getFirst().tradeId()));
        }
    }
}
