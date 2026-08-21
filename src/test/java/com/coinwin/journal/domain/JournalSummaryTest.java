package com.coinwin.journal.domain;

import static com.coinwin.journal.JournalFixtures.PLANNED_AT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Percentage;
import com.coinwin.journal.JournalFixtures;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 집계. 손익과 계획 준수를 <b>먼저 가른 뒤에</b> 센다.
 *
 * <p>기준 거래 셋: 익절 +443.80(준수), 손절 -155.00(준수), 손절 지나침 -258.00(위반).
 */
class JournalSummaryTest {

    @Test
    void 계획을_지킨_거래와_어긴_거래의_손익을_따로_센다() {
        JournalSummary summary = JournalSummary.of(List.of(
                JournalFixtures.closedAtTarget(),
                JournalFixtures.closedAtStop(),
                JournalFixtures.heldPastStop()));

        assertThat(summary.followed().trades()).isEqualTo(2);
        assertThat(summary.followed().realizedPnl()).isEqualTo(Money.of("288.80"));
        assertThat(summary.broken().trades()).isEqualTo(1);
        assertThat(summary.broken().realizedPnl()).isEqualTo(Money.of("-258.00"));
    }

    /**
     * 이 모듈의 존재 이유. 전체 손익은 흑자인데 그 흑자가 전부 규칙을 어긴 거래에서 나왔다면
     * 그것은 "방식이 통한다" 는 증거가 아니다. 두 칸이 갈라져 있어야 그 사실이 보인다.
     */
    @Test
    void 어기고_이긴_거래는_준수_쪽_손익에_섞이지_않는다() {
        JournalSummary summary = JournalSummary.of(List.of(
                JournalFixtures.closedAtStop(),
                JournalFixtures.closedAt(
                        com.coinwin.common.domain.Price.of("63000"),
                        ExitReason.MANUAL_EARLY, JournalFixtures.EXIT_AT)));

        assertThat(summary.totalRealizedPnl()).isEqualTo(Money.of("195.00"));
        assertThat(summary.followed().realizedPnl()).isEqualTo(Money.of("-155.00"));
        assertThat(summary.broken().realizedPnl()).isEqualTo(Money.of("350.00"));
    }

    @Test
    void 어긴_거래의_반사실_손실과_어긴_대가를_낸다() {
        JournalSummary summary = JournalSummary.of(List.of(JournalFixtures.heldPastStop()));

        assertThat(summary.lossIfEveryStopHonored()).isEqualTo(Money.of("-155.00"));
        assertThat(summary.costOfDeviation()).isEqualTo(Money.of("-103.00"));
    }

    /** 반사실은 어긴 거래에 대해서만 센다. 지킨 거래에는 되물을 것이 없다. */
    @Test
    void 계획을_지킨_거래만_있으면_반사실_손실은_0이다() {
        JournalSummary summary = JournalSummary.of(List.of(
                JournalFixtures.closedAtTarget(), JournalFixtures.closedAtStop()));

        assertThat(summary.lossIfEveryStopHonored()).isEqualTo(Money.of("0.00"));
        assertThat(summary.costOfDeviation()).isEqualTo(Money.of("0.00"));
        assertThat(summary.broken().isEmpty()).isTrue();
    }

    @Test
    void 계획_준수율은_지킨_건수의_비율이다() {
        JournalSummary summary = JournalSummary.of(List.of(
                JournalFixtures.closedAtTarget(),
                JournalFixtures.closedAtStop(),
                JournalFixtures.heldPastStop(),
                JournalFixtures.heldPastStop()));

        assertThat(summary.planAdherence()).isEqualTo(Percentage.of("50"));
        assertThat(summary.totalTrades()).isEqualTo(4);
    }

    /** 본전으로 끝난 거래는 승리가 아니다. 수수료를 내고 제자리면 이긴 것이 아니다. */
    @Test
    void 승률은_손익이_양수인_거래만_센다() {
        JournalSummary summary = JournalSummary.of(List.of(
                JournalFixtures.closedAtTarget(),
                JournalFixtures.closedAtStop(),
                JournalFixtures.closedAt(JournalFixtures.longPlan().entries().averagePriceAfter(2),
                        ExitReason.PLANNED_TARGET, JournalFixtures.EXIT_AT)));

        assertThat(summary.followed().trades()).isEqualTo(3);
        assertThat(summary.followed().wins()).isEqualTo(1);
        assertThat(summary.followed().losses()).isEqualTo(2);
        assertThat(summary.followed().winRate())
                .isEqualTo(Percentage.ofRatio(1, 3));
    }

    @Test
    void 거래_간격은_직전_청산부터_다음_진입까지다() {
        JournalSummary summary = JournalSummary.of(List.of(
                JournalFixtures.closedEndingAt(instant(0), ExitReason.PLANNED_TARGET),
                JournalFixtures.closedEndingAt(instant(20), ExitReason.PLANNED_TARGET),
                JournalFixtures.closedEndingAt(instant(60), ExitReason.PLANNED_TARGET)));

        // 두 번째 거래는 청산 20h 짜리, 진입은 그 9h 전 + 1h = 12h → 간격 12h
        assertThat(summary.intervals().gaps()).isEqualTo(2);
        assertThat(summary.intervals().shortest()).isEqualTo(Duration.ofHours(12));
        assertThat(summary.intervals().average()).isEqualTo(Duration.ofHours(22));
    }

    /** 순서가 뒤섞여 들어와도 간격은 시간순으로 센다. 조회 결과의 순서는 기록이 아니다. */
    @Test
    void 목록_순서가_뒤바뀌어도_같은_집계가_나온다() {
        List<ClosedTrade> chronological = List.of(
                JournalFixtures.closedEndingAt(instant(0), ExitReason.PLANNED_TARGET),
                JournalFixtures.closedEndingAt(instant(20), ExitReason.PLANNED_TARGET));

        assertThat(JournalSummary.of(chronological.reversed()))
                .isEqualTo(JournalSummary.of(chronological));
    }

    @Test
    void 거래가_하나면_간격이_없다() {
        JournalSummary summary = JournalSummary.of(List.of(JournalFixtures.closedAtTarget()));

        assertThat(summary.intervals().isEmpty()).isTrue();
        assertThat(summary.intervals().gaps()).isZero();
    }

    @Test
    void 빈_목록의_집계는_전부_0이다() {
        JournalSummary summary = JournalSummary.of(List.of());

        assertThat(summary.totalTrades()).isZero();
        assertThat(summary.totalRealizedPnl()).isEqualTo(Money.of("0.00"));
        assertThat(summary.planAdherence()).isEqualTo(Percentage.of("0"));
        assertThat(summary.followed().winRate()).isEqualTo(Percentage.of("0"));
        assertThat(summary).isEqualTo(JournalSummary.empty());
    }

    /**
     * 겹치는 두 거래 사이에는 간격이 존재하지 않는다. 음수를 평균에 섞지도, 집계 전체를
     * 실패시키지도 않고 따로 센다.
     */
    @Test
    void 겹치는_두_거래는_간격에서_빼고_따로_센다() {
        JournalSummary summary = JournalSummary.of(List.of(
                JournalFixtures.closedEndingAt(instant(0), ExitReason.PLANNED_TARGET),
                JournalFixtures.closedEndingAt(instant(3), ExitReason.PLANNED_TARGET)));

        assertThat(summary.intervals().overlaps()).isEqualTo(1);
        assertThat(summary.intervals().gaps()).isZero();
        assertThat(summary.totalTrades()).isEqualTo(2);
    }

    /** 간격을 직접 물으면 여전히 거부한다. 답이 없는 질문에 0 을 돌려주지 않는다. */
    @Test
    void 겹치는_거래에_간격을_직접_물으면_거부한다() {
        ClosedTrade first = JournalFixtures.closedEndingAt(instant(0), ExitReason.PLANNED_TARGET);
        ClosedTrade overlapping =
                JournalFixtures.closedEndingAt(instant(3), ExitReason.PLANNED_TARGET);

        assertThatThrownBy(() -> overlapping.timeSincePreviousTrade(first))
                .isInstanceOf(InvalidTradeException.class)
                .hasMessageContaining("직전 거래는 이 거래가 열리기 전에 닫혀 있어야 한다");
    }

    @Test
    void null_목록은_거부한다() {
        assertThatThrownBy(() -> JournalSummary.of(null))
                .isInstanceOf(com.coinwin.common.domain.InvalidValueException.class);
    }

    private static Instant instant(int hoursFromBase) {
        return PLANNED_AT.plus(Duration.ofHours(hoursFromBase));
    }
}
