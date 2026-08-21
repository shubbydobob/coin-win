package com.coinwin.journal.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.InvalidValueException;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Price;
import com.coinwin.indicator.domain.BandPosition;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 식별자·비용·진입 맥락. 값 하나짜리 규칙들이라 한곳에 모은다. */
class JournalValueTest {

    @Test
    void 거래_식별자는_문자열에서_읽을_수_있다() {
        UUID uuid = UUID.fromString("2f1c4e6a-0000-4000-8000-000000000001");

        assertThat(TradeId.of(uuid.toString())).isEqualTo(new TradeId(uuid));
        assertThat(TradeId.of(uuid.toString())).hasToString(uuid.toString());
    }

    @Test
    void UUID_형식이_아닌_식별자는_거부한다() {
        assertThatThrownBy(() -> TradeId.of("삼번거래"))
                .isInstanceOf(InvalidValueException.class)
                .hasMessageContaining("UUID 형식이 아니다");
    }

    @Test
    void 새_식별자는_매번_다르다() {
        assertThat(TradeId.random()).isNotEqualTo(TradeId.random());
    }

    @Test
    void null_식별자는_거부한다() {
        assertThatThrownBy(() -> new TradeId(null)).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> TradeId.of(null)).isInstanceOf(InvalidValueException.class);
    }

    @Test
    void 거래_비용은_수수료와_펀딩비의_합이다() {
        assertThat(TradeCosts.of("5.00", "1.20").total()).isEqualTo(Money.of("6.20"));
        assertThat(TradeCosts.none().total()).isEqualTo(Money.of("0.00"));
    }

    /** 펀딩비는 음수가 정상값이다. 포지션 방향이 소수 쪽이면 받는다. */
    @Test
    void 받은_펀딩비는_비용을_줄인다() {
        assertThat(TradeCosts.of("5.00", "-2.00").total()).isEqualTo(Money.of("3.00"));
    }

    @Test
    void 음수_수수료는_거부한다() {
        assertThatThrownBy(() -> TradeCosts.of("-0.01", "0"))
                .isInstanceOf(InvalidValueException.class)
                .hasMessageContaining("수수료는 음수일 수 없다");
    }

    @Test
    void 진입_근거가_비어_있으면_거부한다() {
        assertThatThrownBy(() -> context("   "))
                .isInstanceOf(InvalidTradeException.class)
                .hasMessageContaining("진입 근거는 비워 둘 수 없다");
    }

    @Test
    void 진입_근거의_앞뒤_공백은_지운다() {
        assertThat(context("  4h 지지 확인  ").rationale()).isEqualTo("4h 지지 확인");
    }

    @Test
    void 진입_근거가_상한을_넘으면_거부한다() {
        String tooLong = "가".repeat(MarketContext.MAX_RATIONALE_LENGTH + 1);

        assertThatThrownBy(() -> context(tooLong))
                .isInstanceOf(InvalidValueException.class)
                .hasMessageContaining("진입 근거는 500 자를 넘을 수 없다");
    }

    @Test
    void 두_지표가_같은_편을_가리키는지_판정한다() {
        MarketContext agreeing = new MarketContext(Price.of("60000"),
                BandPosition.ABOVE, BandPosition.ABOVE, "둘 다 위");
        MarketContext disagreeing = new MarketContext(Price.of("60000"),
                BandPosition.ABOVE, BandPosition.INSIDE, "엇갈림");

        assertThat(agreeing.filtersAgree()).isTrue();
        assertThat(disagreeing.filtersAgree()).isFalse();
    }

    @Test
    void 진입_맥락의_필수값이_비면_거부한다() {
        assertThatThrownBy(() -> new MarketContext(null,
                BandPosition.ABOVE, BandPosition.ABOVE, "근거"))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new MarketContext(Price.of("60000"),
                null, BandPosition.ABOVE, "근거"))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new MarketContext(Price.of("60000"),
                BandPosition.ABOVE, null, "근거"))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> context(null)).isInstanceOf(InvalidValueException.class);
    }

    /** 계획 준수 판정은 enum 이 소유한다. 집계가 이유 목록을 다시 열거하지 않게 하려는 것이다. */
    @Test
    void 계획한_손절과_익절만_계획_준수다() {
        assertThat(ExitReason.PLANNED_STOP.honorsPlan()).isTrue();
        assertThat(ExitReason.PLANNED_TARGET.honorsPlan()).isTrue();
        assertThat(ExitReason.MANUAL_EARLY.honorsPlan()).isFalse();
        assertThat(ExitReason.HELD_PAST_STOP.honorsPlan()).isFalse();
        assertThat(ExitReason.LIQUIDATED.honorsPlan()).isFalse();
    }

    @Test
    void 청산_정보의_필수값이_비면_거부한다() {
        Exit exit = new Exit(Price.of("64000"), java.time.Instant.EPOCH);

        assertThatThrownBy(() -> new TradeClosure(null, ExitReason.PLANNED_STOP, TradeCosts.none()))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new TradeClosure(exit, null, TradeCosts.none()))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new TradeClosure(exit, ExitReason.PLANNED_STOP, null))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new Exit(null, java.time.Instant.EPOCH))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new Exit(Price.of("64000"), null))
                .isInstanceOf(InvalidValueException.class);
    }

    private static MarketContext context(String rationale) {
        return new MarketContext(Price.of("60000"),
                BandPosition.ABOVE, BandPosition.INSIDE, rationale);
    }
}
