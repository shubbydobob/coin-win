package com.coinwin.ai.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;

import com.coinwin.ai.domain.RetrievedTrade;
import com.coinwin.ai.domain.TradeDocument;
import com.coinwin.journal.JournalFixtures;
import com.coinwin.journal.domain.ClosedTrade;
import com.coinwin.journal.domain.ExitReason;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 색인과 검색의 계약. <b>두 어댑터가 모두 이 스위트를 통과해야 한다.</b>
 *
 * <p>유사도 계산 방식은 어댑터마다 다르다 — 인메모리는 낱말이 겹치는 수를 세고 pgvector 는
 * 임베딩 사이의 각을 잰다. 그래서 <b>점수의 절대값은 계약이 아니다.</b> 계약인 것은 개수와
 * 덮어쓰기와 "비우면 사라진다" 다. 그 셋이 지켜지지 않으면 어느 쪽 어댑터를 꽂았느냐에 따라
 * 같은 질문이 다른 개수의 근거를 내놓는다.
 *
 * <p>근거: {@code .claude/docs/testing.md} — "하나의 테스트 스위트를 모든 어댑터 구현체에
 * 대해 실행한다."
 */
public abstract class TradeIndexContract {

    private static final Instant DAY_ONE = Instant.parse("2026-08-01T12:00:00Z");

    protected abstract IndexTradesPort indexPort();

    protected abstract SearchTradesPort searchPort();

    /** 진입 근거 텍스트가 서로 다른 거래 셋. 검색이 무엇으로든 구분할 수 있어야 한다. */
    private static List<TradeDocument> threeTrades() {
        return TradeDocument.over(List.of(
                trade(DAY_ONE, ExitReason.PLANNED_STOP, "58000"),
                trade(DAY_ONE.plus(Duration.ofDays(1)), ExitReason.PLANNED_TARGET, "64000"),
                trade(DAY_ONE.plus(Duration.ofDays(2)), ExitReason.HELD_PAST_STOP, "57000")));
    }

    private static ClosedTrade trade(Instant exitAt, ExitReason reason, String exitPrice) {
        return JournalFixtures.closedEndingAt(exitAt, reason, exitPrice);
    }

    @Test
    void 색인한_거래가_검색된다() {
        List<TradeDocument> documents = threeTrades();
        indexPort().save(documents);

        List<RetrievedTrade> found = searchPort().search("계획을 지킨 롱 거래", 10);

        assertThat(found).isNotEmpty();
        assertThat(found).allSatisfy(trade -> {
            assertThat(trade.tradeId()).isNotBlank();
            assertThat(trade.content()).isNotBlank();
        });
    }

    @Test
    void 근거_거래_수를_넘겨_주지_않는다() {
        indexPort().save(threeTrades());

        assertThat(searchPort().search("거래", 2)).hasSizeLessThanOrEqualTo(2);
        assertThat(searchPort().search("거래", 1)).hasSizeLessThanOrEqualTo(1);
    }

    /** 청산 한 건이 색인될 때마다 사본이 쌓이면 같은 거래가 근거 목록을 다 차지한다. */
    @Test
    void 같은_거래를_다시_색인하면_사본이_생기지_않는다() {
        List<TradeDocument> documents = threeTrades();
        indexPort().save(documents);
        indexPort().save(documents);

        List<String> ids = searchPort().search("거래", 20).stream()
                .map(RetrievedTrade::tradeId)
                .toList();

        assertThat(ids).doesNotHaveDuplicates();
    }

    @Test
    void 비우면_아무것도_검색되지_않는다() {
        indexPort().save(threeTrades());

        indexPort().deleteAll();

        assertThat(searchPort().search("거래", 10)).isEmpty();
    }

    /** 빈 목록으로 임베딩 API 를 부르지 않는다. 부르면 제공자에 따라 오류가 난다. */
    @Test
    void 빈_목록을_색인해도_아무_일도_일어나지_않는다() {
        indexPort().save(List.of());

        assertThat(searchPort().search("거래", 10)).isEmpty();
    }
}
