package com.coinwin.account.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.account.AccountFixtures;
import com.coinwin.journal.JournalFixtures;
import com.coinwin.journal.domain.OpenTrade;
import com.coinwin.position.domain.Direction;
import org.junit.jupiter.api.Test;

/**
 * 짝 하나가 성립하는 조건.
 *
 * <p>{@link PositionReconciliationTest} 가 "어떤 짝이 만들어지는가" 를 본다면 여기는 "만들어진
 * 짝이 말이 되는가" 를 본다. 대조를 거치지 않고 이 타입을 직접 만드는 경로가 있으므로
 * (표시·변환) 그 자리에서도 규칙이 서야 한다.
 */
class PositionMatchTest {

    @Test
    void 짝지어진_결과는_양쪽이_모두_있어야_한다() {
        assertThatThrownBy(() -> new PositionMatch.Agreed(null, longPosition()))
                .isInstanceOf(InvalidAccountDataException.class);
        assertThatThrownBy(() -> new PositionMatch.Agreed(longTrade(), null))
                .isInstanceOf(InvalidAccountDataException.class);
        assertThatThrownBy(() -> new PositionMatch.QuantityDiffers(longTrade(), null))
                .isInstanceOf(InvalidAccountDataException.class);
    }

    /**
     * <b>방향이 다른 것은 같은 포지션이 아니다.</b> 한 줄로 합치면 "방향이 뒤집혔다" 는 가장
     * 심각한 사실이 수량 불일치처럼 보인다. 대조는 그 경우를 두 건으로 가르므로 여기까지
     * 오지 않지만, 직접 만들 때 조용히 통과하면 그 보장이 사라진다.
     */
    @Test
    void 방향이_다르면_짝지을_수_없다() {
        assertThatThrownBy(() -> new PositionMatch.Agreed(longTrade(), shortPosition()))
                .isInstanceOf(InvalidAccountDataException.class)
                .hasMessageContaining("방향이 다른 것은 같은 포지션이 아니다");
    }

    @Test
    void 한쪽만_있는_결과도_그_한쪽은_있어야_한다() {
        assertThatThrownBy(() -> new PositionMatch.RecordedOnly(null))
                .isInstanceOf(InvalidAccountDataException.class);
        assertThatThrownBy(() -> new PositionMatch.ExchangeOnly(null))
                .isInstanceOf(InvalidAccountDataException.class);
    }

    /** 확인할 것이 있는가 — 일치만 거짓이고 나머지 셋은 전부 참이다. */
    @Test
    void 일치만_확인할_것이_없다() {
        assertThat(new PositionMatch.Agreed(longTrade(), longPosition()).isDiscrepancy())
                .isFalse();
        assertThat(new PositionMatch.RecordedOnly(longTrade()).isDiscrepancy()).isTrue();
        assertThat(new PositionMatch.ExchangeOnly(longPosition()).isDiscrepancy()).isTrue();
        assertThat(new PositionMatch.QuantityDiffers(longTrade(), longPosition())
                .isDiscrepancy()).isTrue();
    }

    /** 방향은 네 경우 모두에서 나온다. 한쪽만 있어도 그 한쪽이 방향을 안다. */
    @Test
    void 네_경우_모두_방향을_말한다() {
        assertThat(new PositionMatch.Agreed(longTrade(), longPosition()).direction())
                .isEqualTo(Direction.LONG);
        assertThat(new PositionMatch.RecordedOnly(longTrade()).direction())
                .isEqualTo(Direction.LONG);
        assertThat(new PositionMatch.ExchangeOnly(shortPosition()).direction())
                .isEqualTo(Direction.SHORT);
        assertThat(new PositionMatch.QuantityDiffers(longTrade(), longPosition()).direction())
                .isEqualTo(Direction.LONG);
    }

    private static OpenTrade longTrade() {
        return JournalFixtures.open();
    }

    private static ExchangePosition longPosition() {
        return AccountFixtures.longPosition("0.1");
    }

    private static ExchangePosition shortPosition() {
        return AccountFixtures.shortPosition("0.1", AccountFixtures.OBSERVED_AT);
    }
}
