package com.coinwin.position.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.InvalidValueException;
import com.coinwin.common.domain.Percentage;
import com.coinwin.common.domain.Price;
import org.junit.jupiter.api.Test;

class PlannedEntryTest {

    @Test
    void 진입가와_비중으로_만들어진다() {
        PlannedEntry entry = PlannedEntry.of("60000", "50");

        assertThat(entry.price()).isEqualTo(Price.of("60000"));
        assertThat(entry.allocation()).isEqualTo(Percentage.of("50"));
    }

    @Test
    void 비중이_0퍼센트인_진입_계획은_거부된다() {
        assertThatThrownBy(() -> PlannedEntry.of("60000", "0"))
                .isInstanceOf(InvalidPositionPlanException.class)
                .hasMessageContaining("0%");
    }

    @Test
    void 진입가나_비중이_없으면_거부된다() {
        assertThatThrownBy(() -> new PlannedEntry(null, Percentage.of("50")))
                .isInstanceOf(InvalidValueException.class)
                .hasMessageContaining("진입가");
        assertThatThrownBy(() -> new PlannedEntry(Price.of("60000"), null))
                .isInstanceOf(InvalidValueException.class)
                .hasMessageContaining("분할 비중");
    }
}
