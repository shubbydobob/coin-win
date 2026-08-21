package com.coinwin.journal.domain;

import static com.coinwin.journal.JournalFixtures.EXIT_AT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.InvalidValueException;
import com.coinwin.journal.JournalFixtures;
import com.coinwin.position.domain.Direction;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * 조회 조건의 <b>의미</b>. 인메모리 어댑터가 이 술어를 그대로 쓰고 JPA 어댑터는 같은 뜻의
 * SQL 을 만든다 — 둘이 같은 답을 내는지는 계약 테스트가 확인한다.
 */
class TradeQueryTest {

    private final ClosedTrade trade = JournalFixtures.closedAtTarget();

    @Test
    void 조건이_비어_있으면_모두_통과시킨다() {
        assertThat(TradeQuery.all().matches(trade)).isTrue();
    }

    /** 구간은 반열림 {@code [from, to)} 다. 끝 시각이 들면 연속 조회에서 곧바로 중복이 된다. */
    @Test
    void 청산_구간은_시작을_포함하고_끝을_제외한다() {
        assertThat(TradeQuery.all().closedBetween(EXIT_AT, EXIT_AT.plusSeconds(1)).matches(trade))
                .isTrue();
        assertThat(TradeQuery.all().closedBetween(EXIT_AT.plusSeconds(1), null).matches(trade))
                .isFalse();
        assertThat(TradeQuery.all().closedBetween(null, EXIT_AT).matches(trade))
                .isFalse();
    }

    @Test
    void 방향과_청산_이유와_계획_준수로_거른다() {
        assertThat(TradeQuery.all().withDirection(Direction.LONG).matches(trade)).isTrue();
        assertThat(TradeQuery.all().withDirection(Direction.SHORT).matches(trade)).isFalse();
        assertThat(TradeQuery.all().withExitReason(ExitReason.PLANNED_TARGET).matches(trade))
                .isTrue();
        assertThat(TradeQuery.all().withExitReason(ExitReason.LIQUIDATED).matches(trade)).isFalse();
        assertThat(TradeQuery.all().withFollowedPlan(true).matches(trade)).isTrue();
        assertThat(TradeQuery.all().withFollowedPlan(false).matches(trade)).isFalse();
    }

    /** 조건 하나라도 어긋나면 걸리지 않는다. */
    @Test
    void 여러_조건은_모두_만족해야_한다() {
        TradeQuery query = TradeQuery.all()
                .withDirection(Direction.LONG)
                .withFollowedPlan(false);

        assertThat(query.matches(trade)).isFalse();
    }

    /** null 을 넘기면 그 조건을 지운다. 조건을 끄는 경로가 따로 필요하지 않다. */
    @Test
    void null을_주면_조건이_지워진다() {
        TradeQuery cleared = TradeQuery.all()
                .withDirection(Direction.SHORT)
                .withDirection(null);

        assertThat(cleared.direction()).isEmpty();
        assertThat(cleared.matches(trade)).isTrue();
    }

    @Test
    void 구간의_끝이_시작보다_앞서면_거부한다() {
        assertThatThrownBy(() -> TradeQuery.all()
                .closedBetween(EXIT_AT, EXIT_AT.minus(Duration.ofHours(1))))
                .isInstanceOf(InvalidTradeException.class)
                .hasMessageContaining("조회 구간의 끝은 시작보다 앞설 수 없다");
    }

    @Test
    void 조건_자리에_null을_직접_넣으면_거부한다() {
        assertThatThrownBy(() -> new TradeQuery(null, Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty()))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    void null_거래는_거부한다() {
        assertThatThrownBy(() -> TradeQuery.all().matches(null))
                .isInstanceOf(InvalidValueException.class);
    }
}
