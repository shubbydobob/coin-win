package com.coinwin.journal.domain;

import static com.coinwin.journal.JournalFixtures.FIRST_FILL_AT;
import static com.coinwin.journal.JournalFixtures.SECOND_FILL_AT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.InvalidValueException;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 진입 체결 내역. 평단은 <b>수량</b> 가중이다 — 계획의 비중 가중과 다르다. */
class ExecutedEntriesTest {

    @Test
    void 평단은_수량으로_가중한_평균이다() {
        ExecutedEntries entries = ExecutedEntries.of(
                new Fill(Price.of("60000"), Quantity.of("0.05"), FIRST_FILL_AT),
                new Fill(Price.of("59000"), Quantity.of("0.05"), SECOND_FILL_AT));

        assertThat(entries.averagePrice()).isEqualTo(Price.of("59500.00"));
        assertThat(entries.totalQuantity()).isEqualTo(Quantity.of("0.1"));
    }

    /** 수량이 다르면 단순 평균과 갈린다. 같은 값이 나오면 가중이 빠진 것이다. */
    @Test
    void 수량이_다르면_많이_체결된_가격_쪽으로_평단이_기운다() {
        ExecutedEntries entries = ExecutedEntries.of(
                new Fill(Price.of("60000"), Quantity.of("0.09"), FIRST_FILL_AT),
                new Fill(Price.of("50000"), Quantity.of("0.01"), SECOND_FILL_AT));

        assertThat(entries.averagePrice()).isEqualTo(Price.of("59000.00"));
    }

    @Test
    void 첫_체결과_마지막_체결의_시각을_구분한다() {
        ExecutedEntries entries = ExecutedEntries.of(
                new Fill(Price.of("60000"), Quantity.of("0.05"), FIRST_FILL_AT),
                new Fill(Price.of("59000"), Quantity.of("0.05"), SECOND_FILL_AT));

        assertThat(entries.firstFilledAt()).isEqualTo(FIRST_FILL_AT);
        assertThat(entries.lastFilledAt()).isEqualTo(SECOND_FILL_AT);
        assertThat(entries.count()).isEqualTo(2);
    }

    /** 정렬해 주지 않고 거부한다. 순서를 고치면 평단의 변천사가 조용히 바뀐다. */
    @Test
    void 시간_역순의_체결은_거부한다() {
        List<Fill> reversed = List.of(
                new Fill(Price.of("59000"), Quantity.of("0.05"), SECOND_FILL_AT),
                new Fill(Price.of("60000"), Quantity.of("0.05"), FIRST_FILL_AT));

        assertThatThrownBy(() -> new ExecutedEntries(reversed))
                .isInstanceOf(InvalidTradeException.class)
                .hasMessageContaining("시간 오름차순");
    }

    /** 같은 시각의 두 건은 허용한다. 한 주문이 여러 체결로 쪼개지면 실제로 그렇다. */
    @Test
    void 같은_시각의_두_체결은_허용한다() {
        ExecutedEntries entries = ExecutedEntries.of(
                new Fill(Price.of("60000"), Quantity.of("0.03"), FIRST_FILL_AT),
                new Fill(Price.of("60000"), Quantity.of("0.02"), FIRST_FILL_AT));

        assertThat(entries.totalQuantity()).isEqualTo(Quantity.of("0.05"));
    }

    @Test
    void 빈_체결_내역은_거부한다() {
        assertThatThrownBy(() -> new ExecutedEntries(List.of()))
                .isInstanceOf(InvalidTradeException.class)
                .hasMessageContaining("최소 1건");
    }

    @Test
    void null_체결_내역은_거부한다() {
        assertThatThrownBy(() -> new ExecutedEntries(null))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    void 수량이_0인_체결은_거부한다() {
        assertThatThrownBy(() ->
                new Fill(Price.of("60000"), Quantity.of("0"), FIRST_FILL_AT))
                .isInstanceOf(InvalidTradeException.class)
                .hasMessageContaining("체결 수량은 0 일 수 없다");
    }

    @Test
    void 체결의_명목가는_수량_곱하기_체결가다() {
        Fill fill = new Fill(Price.of("60000"), Quantity.of("0.05"), FIRST_FILL_AT);

        assertThat(fill.notional()).isEqualTo(Money.of("3000.00"));
    }

    @Test
    void 체결_시각이_없으면_거부한다() {
        assertThatThrownBy(() -> new Fill(Price.of("60000"), Quantity.of("0.05"), null))
                .isInstanceOf(InvalidValueException.class);
    }

    /** 목록을 넘긴 뒤 바깥에서 고쳐도 내부가 바뀌지 않아야 한다. */
    @Test
    void 체결_목록은_복사해서_보관한다() {
        List<Fill> mutable = new java.util.ArrayList<>(List.of(
                new Fill(Price.of("60000"), Quantity.of("0.05"), FIRST_FILL_AT)));
        ExecutedEntries entries = new ExecutedEntries(mutable);

        mutable.add(new Fill(Price.of("1"), Quantity.of("1"), SECOND_FILL_AT));

        assertThat(entries.count()).isEqualTo(1);
    }
}
