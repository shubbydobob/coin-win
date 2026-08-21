package com.coinwin.position.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.InvalidValueException;
import com.coinwin.common.domain.Percentage;
import com.coinwin.common.domain.Price;
import java.util.List;
import org.junit.jupiter.api.Test;

class EntryLadderTest {

    /** 불균등 3분할. 가중 평단이 단순 평균과 갈라지는지 확인하기 위한 배치다. */
    private static EntryLadder 불균등_3분할() {
        return EntryLadder.of(
                PlannedEntry.of("60000", "50"),
                PlannedEntry.of("58000", "30"),
                PlannedEntry.of("56000", "20"));
    }

    @Test
    void 분할_비중의_합은_정확히_100퍼센트여야_한다() {
        assertThatThrownBy(() -> EntryLadder.of(
                PlannedEntry.of("60000", "50"),
                PlannedEntry.of("58000", "40")))
                .isInstanceOf(InvalidPositionPlanException.class)
                .hasMessageContaining("90");
    }

    @Test
    void 합이_100을_넘어도_거부된다() {
        assertThatThrownBy(() -> EntryLadder.of(
                PlannedEntry.of("60000", "60"),
                PlannedEntry.of("58000", "50")))
                .isInstanceOf(InvalidPositionPlanException.class)
                .hasMessageContaining("110");
    }

    @Test
    void 평단은_단순_평균이_아니라_비중_가중_평균이다() {
        // (60000×50 + 58000×30 + 56000×20) / 100 = 58,600
        // 단순 평균이라면 58,000 이 나온다
        assertThat(불균등_3분할().averagePriceAfter(3)).isEqualTo(Price.of("58600"));
    }

    @Test
    void 부분_체결_평단은_체결된_비중만으로_다시_가중한다() {
        // (60000×50 + 58000×30) / 80 = 59,250
        assertThat(불균등_3분할().averagePriceAfter(2)).isEqualTo(Price.of("59250"));
        assertThat(불균등_3분할().averagePriceAfter(1)).isEqualTo(Price.of("60000"));
    }

    @Test
    void 체결된_비중은_체결_건수까지의_합이다() {
        assertThat(불균등_3분할().allocationFilledAfter(2)).isEqualTo(Percentage.of("80"));
        assertThat(불균등_3분할().allocationFilledAfter(3)).isEqualTo(Percentage.of("100"));
    }

    @Test
    void 최저가와_최고가는_체결_순서가_아니라_가격으로_정해진다() {
        EntryLadder ladder = EntryLadder.of(
                PlannedEntry.of("58000", "50"),
                PlannedEntry.of("60000", "50"));

        assertThat(ladder.lowestPrice()).isEqualTo(Price.of("58000"));
        assertThat(ladder.highestPrice()).isEqualTo(Price.of("60000"));
        assertThat(ladder.size()).isEqualTo(2);
    }

    @Test
    void 체결_건수가_범위를_벗어나면_거부된다() {
        assertThatThrownBy(() -> 불균등_3분할().averagePriceAfter(0))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> 불균등_3분할().averagePriceAfter(4))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    void 진입_계획이_비어_있으면_거부된다() {
        assertThatThrownBy(EntryLadder::of)
                .isInstanceOf(InvalidPositionPlanException.class)
                .hasMessageContaining("최소 1건");
    }

    @Test
    void null_진입_계획은_거부된다() {
        assertThatThrownBy(() -> new EntryLadder(null))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    void 원본_리스트를_바꿔도_사다리는_변하지_않는다() {
        List<PlannedEntry> 원본 = new java.util.ArrayList<>(
                List.of(PlannedEntry.of("60000", "100")));
        EntryLadder ladder = new EntryLadder(원본);

        원본.clear();

        assertThat(ladder.size()).isEqualTo(1);
    }
}
