package com.coinwin.journal;

import com.coinwin.common.domain.Percentage;
import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import com.coinwin.indicator.domain.BandPosition;
import com.coinwin.journal.domain.ClosedTrade;
import com.coinwin.journal.domain.ExecutedEntries;
import com.coinwin.journal.domain.Exit;
import com.coinwin.journal.domain.ExitReason;
import com.coinwin.journal.domain.Fill;
import com.coinwin.journal.domain.MarketContext;
import com.coinwin.journal.domain.OpenTrade;
import com.coinwin.journal.domain.PlannedTrade;
import com.coinwin.journal.domain.TradeClosure;
import com.coinwin.journal.domain.TradeCosts;
import com.coinwin.position.domain.Direction;
import com.coinwin.position.domain.EntryLadder;
import com.coinwin.position.domain.PlannedEntry;
import com.coinwin.position.domain.PositionPlan;
import java.time.Duration;
import java.time.Instant;

/**
 * 계산이 손으로 검산되는 시나리오 하나를 모든 journal 테스트가 공유한다.
 *
 * <p>롱 2분할 60000 / 59000, 각 0.05 BTC → 평단 59500, 총수량 0.1. 이 숫자를 고른 이유는
 * 평단·손익·반사실이 전부 <b>암산으로 확인되는 값</b>이 되기 때문이다. 기댓값을 코드로
 * 계산해 넣으면 구현과 같은 실수를 두 번 하게 된다.
 *
 * <ul>
 *   <li>익절 64000 → 총손익 {@code (64000 - 59500) × 0.1 = 450.00}
 *   <li>손절 58000 → 반사실 {@code (58000 - 59500) × 0.1 = -150.00}
 * </ul>
 */
public final class JournalFixtures {

    public static final Instant PLANNED_AT = Instant.parse("2026-08-01T00:00:00Z");
    public static final Instant FIRST_FILL_AT = PLANNED_AT.plus(Duration.ofHours(1));
    public static final Instant SECOND_FILL_AT = PLANNED_AT.plus(Duration.ofHours(2));
    public static final Instant EXIT_AT = PLANNED_AT.plus(Duration.ofHours(9));

    private static final Percentage HALF = Percentage.of("50");
    private static final Quantity LEG = Quantity.of("0.05");

    private JournalFixtures() {
    }

    public static PositionPlan longPlan() {
        return new PositionPlan(
                Direction.LONG,
                EntryLadder.of(
                        new PlannedEntry(Price.of("60000"), HALF),
                        new PlannedEntry(Price.of("59000"), HALF)),
                Price.of("58000"),
                Price.of("64000"),
                10);
    }

    public static PositionPlan shortPlan() {
        return new PositionPlan(
                Direction.SHORT,
                EntryLadder.of(
                        new PlannedEntry(Price.of("60000"), HALF),
                        new PlannedEntry(Price.of("61000"), HALF)),
                Price.of("62000"),
                Price.of("56000"),
                10);
    }

    /** 계획대로 두 번 체결. 평단 59500, 총수량 0.1. */
    public static ExecutedEntries bothLegsFilled() {
        return ExecutedEntries.of(
                new Fill(Price.of("60000"), LEG, FIRST_FILL_AT),
                new Fill(Price.of("59000"), LEG, SECOND_FILL_AT));
    }

    /** 1차만 체결. 평단 60000, 총수량 0.05. */
    public static ExecutedEntries firstLegOnly() {
        return ExecutedEntries.of(new Fill(Price.of("60000"), LEG, FIRST_FILL_AT));
    }

    public static MarketContext context() {
        return new MarketContext(Price.of("60000"), BandPosition.ABOVE, BandPosition.INSIDE,
                "4h 59,000 지지 3회 확인");
    }

    public static PlannedTrade planned() {
        return PlannedTrade.of(longPlan(), PLANNED_AT);
    }

    public static OpenTrade open() {
        return planned().fill(bothLegsFilled(), context());
    }

    /** 숏 2분할 60000 / 61000, 각 0.05 → 평단 60500, 총수량 0.1. 방향으로 가르는 테스트용. */
    public static OpenTrade openShort() {
        return PlannedTrade.of(shortPlan(), PLANNED_AT).fill(
                ExecutedEntries.of(
                        new Fill(Price.of("60000"), LEG, FIRST_FILL_AT),
                        new Fill(Price.of("61000"), LEG, SECOND_FILL_AT)),
                context());
    }

    /** 익절 청산. 수수료 5.00 / 펀딩비 1.20 → 실현 손익 443.80. */
    public static ClosedTrade closedAtTarget() {
        return open().close(new TradeClosure(
                new Exit(Price.of("64000"), EXIT_AT),
                ExitReason.PLANNED_TARGET,
                TradeCosts.of("5.00", "1.20")));
    }

    /** 계획한 손절가에서 닫혔다. 실현 손익 -155.00. */
    public static ClosedTrade closedAtStop() {
        return open().close(new TradeClosure(
                new Exit(Price.of("58000"), EXIT_AT),
                ExitReason.PLANNED_STOP,
                TradeCosts.of("5.00", "0.00")));
    }

    /** 손절을 지나쳐 들고 있다가 57000 에서 닫았다. 계획을 어긴 거래다. */
    public static ClosedTrade heldPastStop() {
        return open().close(new TradeClosure(
                new Exit(Price.of("57000"), EXIT_AT),
                ExitReason.HELD_PAST_STOP,
                TradeCosts.of("5.00", "3.00")));
    }

    public static ClosedTrade closedAt(Price exitPrice, ExitReason reason, Instant at) {
        return open().close(new TradeClosure(
                new Exit(exitPrice, at), reason, TradeCosts.none()));
    }

    /**
     * 표준 시나리오를 통째로 시간축 위에서 옮긴 거래. 거래 간격을 다루는 테스트에 쓴다.
     *
     * <p>{@link #closedAt} 은 고정된 체결 시각 위에 청산만 얹으므로 청산이 체결보다 앞설 수
     * 없다는 규칙에 걸린다. 앞선 거래를 만들려면 계획부터 함께 옮겨야 한다.
     */
    public static ClosedTrade closedEndingAt(Instant exitAt, ExitReason reason) {
        return endingAt(longPlan(), new Legs("60000", "59000", "64000"), exitAt, reason);
    }

    /**
     * 청산가까지 지정해 시간축 위에 놓는 거래.
     *
     * <p>{@link #closedEndingAt(Instant, ExitReason)} 은 청산가가 익절가로 고정돼 있어 이유를
     * 무엇으로 주든 <b>이익으로 끝난다.</b> 손실로 끝난 거래가 필요한 테스트는 이쪽을 쓴다.
     */
    public static ClosedTrade closedEndingAt(
            Instant exitAt, ExitReason reason, String exitPrice) {
        return endingAt(longPlan(), new Legs("60000", "59000", exitPrice), exitAt, reason);
    }

    /** 숏 거래. 방향으로 거르는 조회를 검사할 때 쓴다. */
    public static ClosedTrade shortClosedEndingAt(Instant exitAt, ExitReason reason) {
        return endingAt(shortPlan(), new Legs("60000", "61000", "56000"), exitAt, reason);
    }

    /** 아직 체결되지 않은 계획. 시각을 옮겨 여러 건을 만들 수 있다. */
    public static PlannedTrade plannedAt(Instant at) {
        return PlannedTrade.of(longPlan(), at);
    }

    private static ClosedTrade endingAt(
            PositionPlan plan, Legs legs, Instant exitAt, ExitReason reason) {
        Instant base = exitAt.minus(Duration.ofHours(9));
        return PlannedTrade.of(plan, base)
                .fill(ExecutedEntries.of(
                                new Fill(Price.of(legs.first()), LEG,
                                        base.plus(Duration.ofHours(1))),
                                new Fill(Price.of(legs.second()), LEG,
                                        base.plus(Duration.ofHours(2)))),
                        context())
                .close(new TradeClosure(new Exit(Price.of(legs.exit()), exitAt),
                        reason, TradeCosts.of("5.00", "1.20")));
    }

    /** 두 진입가와 청산가. 파라미터 넷을 넘기지 않으려고 묶었다. */
    private record Legs(String first, String second, String exit) {
    }
}
