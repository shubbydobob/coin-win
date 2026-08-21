package com.coinwin.ai.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.coinwin.journal.JournalFixtures;
import com.coinwin.journal.domain.ClosedTrade;
import com.coinwin.journal.domain.ExitReason;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 거래 하나를 검색 가능한 문서 하나로 바꾼다.
 *
 * <p>이 클래스의 존재 이유는 <b>파생 사실</b>이다. "손실 직후에 들어간 거래" 는 의미 검색으로
 * 찾을 수 없다 — 유사도는 문장이 서로 얼마나 닮았는지만 보고, "직후" 는 순서에서만 나온다.
 * 그래서 색인 시점에 시간순 전체 목록 위에서 미리 계산해 문장과 메타데이터 양쪽에 박는다.
 */
class TradeDocumentTest {

    private static final Instant DAY_ONE = Instant.parse("2026-08-01T12:00:00Z");

    /** 손절가 58000 에서 닫힌 거래. 평단 59500 이므로 실현 손익은 음수다. */
    private static ClosedTrade losing(Instant exitAt) {
        return JournalFixtures.closedEndingAt(exitAt, ExitReason.PLANNED_STOP, "58000");
    }

    private static ClosedTrade winning(Instant exitAt) {
        return JournalFixtures.closedEndingAt(exitAt, ExitReason.PLANNED_TARGET);
    }

    @Test
    void 거래_하나가_문서_하나가_되고_식별자를_유지한다() {
        ClosedTrade trade = JournalFixtures.closedAtTarget();

        List<TradeDocument> documents = TradeDocument.over(List.of(trade));

        assertThat(documents).hasSize(1);
        assertThat(documents.getFirst().id()).isEqualTo(trade.id().value().toString());
    }

    @Test
    void 문장에_손익과_계획_준수와_진입_근거가_들어간다() {
        String content = TradeDocument.over(List.of(JournalFixtures.closedAtTarget()))
                .getFirst().content();

        assertThat(content).contains("롱");
        assertThat(content).contains("443.80");
        assertThat(content).contains("계획을 지켰다");
        assertThat(content).contains("4h 59,000 지지 3회 확인");
        assertThat(content).contains("일목");
    }

    @Test
    void 계획을_어긴_거래는_어겼다고_적힌다() {
        String content = TradeDocument.over(List.of(JournalFixtures.heldPastStop()))
                .getFirst().content();

        assertThat(content).contains("계획을 어겼다");
        assertThat(content).contains("HELD_PAST_STOP");
    }

    @Test
    void 메타데이터에_거르는_데_쓸_값이_들어간다() {
        TradeDocument document = TradeDocument.over(List.of(JournalFixtures.closedAtTarget()))
                .getFirst();

        assertThat(document.metadata())
                .containsEntry("direction", "LONG")
                .containsEntry("followedPlan", true)
                .containsEntry("exitReason", "PLANNED_TARGET")
                .containsEntry("leverage", 10)
                .containsKey("realizedPnl")
                .containsKey("openedAt")
                .containsKey("holdingMinutes")
                .containsEntry("ichimoku", "ABOVE")
                .containsEntry("bollinger", "INSIDE");
    }

    /** 첫 거래에는 직전이 없다. 없는 것을 거짓으로 적으면 "손실 직후가 아니었다" 는 거짓말이 된다. */
    @Test
    void 첫_거래는_직전_거래에_대한_사실을_갖지_않는다() {
        TradeDocument first = TradeDocument.over(List.of(losing(DAY_ONE))).getFirst();

        assertThat(first.metadata()).doesNotContainKey("afterLoss");
        assertThat(first.metadata()).doesNotContainKey("minutesSincePreviousTrade");
        assertThat(first.content()).contains("첫 거래");
    }

    /** ADR 005 가 예로 든 질문이 이 한 줄에 걸려 있다. */
    @Test
    void 직전_거래가_손실이면_손실_직후임이_문장과_메타데이터에_모두_남는다() {
        List<TradeDocument> documents = TradeDocument.over(List.of(
                losing(DAY_ONE),
                winning(DAY_ONE.plus(Duration.ofHours(12)))));

        TradeDocument second = documents.get(1);
        assertThat(second.metadata()).containsEntry("afterLoss", true);
        assertThat(second.content()).contains("직전 거래는 손실이었다");
    }

    @Test
    void 직전_거래가_이익이면_손실_직후가_아니다() {
        List<TradeDocument> documents = TradeDocument.over(List.of(
                winning(DAY_ONE),
                losing(DAY_ONE.plus(Duration.ofHours(12)))));

        assertThat(documents.get(1).metadata()).containsEntry("afterLoss", false);
        assertThat(documents.get(1).content()).contains("직전 거래는 이익이었다");
    }

    /**
     * 간격도 함께 남긴다. 손실 직후 <b>얼마나 빨리</b> 다시 들어갔는지가 복수 매매를 가르는
     * 값이고, 그것 역시 거래 하나만 봐서는 알 수 없다.
     */
    @Test
    void 직전_거래와의_간격을_분으로_남긴다() {
        List<TradeDocument> documents = TradeDocument.over(List.of(
                losing(DAY_ONE),
                winning(DAY_ONE.plus(Duration.ofHours(12)))));

        // 앞 거래 청산 12:00, 뒤 거래 진입은 그 12시간 뒤 청산에서 8시간 앞 → 4시간.
        assertThat(documents.get(1).metadata())
                .containsEntry("minutesSincePreviousTrade", 240L);
    }

    @Test
    void 빈_목록에서는_문서가_나오지_않는다() {
        assertThat(TradeDocument.over(List.of())).isEmpty();
    }
}
