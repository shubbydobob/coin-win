package com.coinwin.account.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.account.AccountFixtures;
import com.coinwin.journal.JournalFixtures;
import com.coinwin.journal.domain.OpenTrade;
import com.coinwin.position.domain.Direction;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 대조의 규칙.
 *
 * <p>기록 픽스처는 롱 2분할 60000 / 59000 각 0.05 → <b>총수량 0.1</b> 이다
 * ({@code JournalFixtures}). 거래소 쪽 수량을 그 값과 맞추거나 어긋내어 네 경우를 만든다.
 */
class PositionReconciliationTest {

    private static final Instant OBSERVED = Instant.parse("2026-08-23T01:53:00Z");

    @Test
    void 기록과_거래소가_같으면_일치다() {
        PositionReconciliation result = reconcile(
                List.of(longTrade()), List.of(AccountFixtures.longPosition("0.1", OBSERVED)));

        assertThat(result.isConsistent()).isTrue();
        assertThat(result.discrepancies()).isEmpty();
        assertThat(result.matches()).singleElement()
                .isInstanceOf(PositionMatch.Agreed.class);
    }

    /**
     * 둘 다 비어 있는 것도 일치다. 포지션이 없다는 사실에 기록과 거래소가 합의한 상태이고,
     * 그것은 확인할 것이 없는 상태다.
     */
    @Test
    void 양쪽_모두_비어_있으면_일치다() {
        PositionReconciliation result = reconcile(List.of(), List.of());

        assertThat(result.isConsistent()).isTrue();
        assertThat(result.matches()).isEmpty();
    }

    /**
     * 손절이 체결됐는데 청산을 적지 않으면 이 상태가 된다. <b>가장 흔하고 가장 조용한
     * 오류다</b> — 그동안 집계는 그 거래를 없는 것으로 센다.
     */
    @Test
    void 기록에만_있으면_청산을_적지_않았을_수_있다() {
        PositionReconciliation result = reconcile(List.of(longTrade()), List.of());

        assertThat(result.isConsistent()).isFalse();
        assertThat(result.discrepancies()).singleElement()
                .isInstanceOf(PositionMatch.RecordedOnly.class);
    }

    @Test
    void 거래소에만_있으면_앱_밖에서_연_포지션이다() {
        PositionReconciliation result = reconcile(
                List.of(), List.of(AccountFixtures.longPosition("0.1", OBSERVED)));

        assertThat(result.isConsistent()).isFalse();
        assertThat(result.discrepancies()).singleElement()
                .isInstanceOf(PositionMatch.ExchangeOnly.class);
    }

    /** 스케일 8 까지 정확히 같아야 일치다. 허용 오차를 두면 부분 청산이 조용히 통과한다. */
    @Test
    void 수량이_한_자리라도_다르면_불일치다() {
        PositionReconciliation result = reconcile(
                List.of(longTrade()),
                List.of(AccountFixtures.longPosition("0.09999999", OBSERVED)));

        assertThat(result.discrepancies()).singleElement()
                .isInstanceOf(PositionMatch.QuantityDiffers.class);
    }

    /**
     * <b>방향이 뒤집힌 것은 한 줄로 합치지 않는다.</b> 짝이 지어지지 않는 것 자체가 사실의
     * 표현이다 — 합치면 가장 심각한 사실이 수량 불일치처럼 보인다.
     */
    @Test
    void 방향이_뒤집히면_두_건으로_갈린다() {
        PositionReconciliation result = reconcile(
                List.of(longTrade()), List.of(AccountFixtures.shortPosition("0.1", OBSERVED)));

        assertThat(result.matches()).hasSize(2);
        assertThat(result.discrepancies())
                .anySatisfy(match -> assertThat(match)
                        .isInstanceOf(PositionMatch.RecordedOnly.class))
                .anySatisfy(match -> assertThat(match)
                        .isInstanceOf(PositionMatch.ExchangeOnly.class));
    }

    @Test
    void 롱과_숏이_동시에_열려_있으면_각각_짝지어진다() {
        PositionReconciliation result = reconcile(
                List.of(longTrade(), shortTrade()),
                List.of(AccountFixtures.longPosition("0.1", OBSERVED),
                        AccountFixtures.shortPosition("0.1", OBSERVED)));

        assertThat(result.isConsistent()).isTrue();
        assertThat(result.matches()).hasSize(2);
        assertThat(result.matches().stream().map(PositionMatch::direction))
                .containsExactly(Direction.LONG, Direction.SHORT);
    }

    /**
     * 같은 방향에 둘 이상이면 조용히 첫 번째를 고르지 않는다. 그러면 나머지가 대조에서
     * 사라지고, 열려 있는 줄 모르는 포지션이 생긴다 — 이 기능이 막으려는 상태 그 자체다.
     */
    @Test
    void 같은_방향의_기록이_둘_이상이면_던진다() {
        List<OpenTrade> two = List.of(longTrade(), longTrade());

        assertThatThrownBy(() -> reconcile(two, List.of()))
                .isInstanceOf(InvalidAccountDataException.class)
                .hasMessageContaining("둘 이상");
    }

    @Test
    void 관측_시각을_그대로_들고_있는다() {
        assertThat(reconcile(List.of(), List.of()).observedAt()).isEqualTo(OBSERVED);
    }

    private static PositionReconciliation reconcile(
            List<OpenTrade> recorded, List<ExchangePosition> actual) {
        return PositionReconciliation.of(recorded, actual, OBSERVED);
    }

    /** 롱 2분할, 총수량 0.1. */
    private static OpenTrade longTrade() {
        return JournalFixtures.open();
    }

    /** 숏 2분할, 총수량 0.1. */
    private static OpenTrade shortTrade() {
        return JournalFixtures.openShort();
    }
}
