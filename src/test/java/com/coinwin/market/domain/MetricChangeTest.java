package com.coinwin.market.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.InvalidValueException;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * 정해진 창에서의 변화.
 *
 * <p>위치(분위)만으로는 <b>정지 화면</b>이다. "지금 높다" 는 알아도 오르는 중인지 빠지는
 * 중인지를 모르고, 계기가 된 사건(숏 스퀴즈)은 미결제약정이 <b>급감</b>할 때 벌어진다.
 */
class MetricChangeTest {

    @Test
    void 창의_첫_값_대비로_잰다() {
        // [100, 101, 102, 103] 에서 창 4 → (103 − 100) / 100
        assertThat(표본("100", "101", "102", "103").changeOver(4, MetricKind.Change.RATIO))
                .hasValueSatisfying(값 -> assertThat(값).isEqualByComparingTo("0.030000"));
    }

    @Test
    void 창은_표본의_뒤에서_센다() {
        // [1, 2, 100, 110] 에서 창 2 → 앞의 둘은 보지 않는다
        assertThat(표본("1", "2", "100", "110").changeOver(2, MetricKind.Change.RATIO))
                .hasValueSatisfying(값 -> assertThat(값).isEqualByComparingTo("0.100000"));
    }

    @Test
    void 줄어들면_음수다() {
        assertThat(표본("100", "97").changeOver(2, MetricKind.Change.RATIO))
                .hasValueSatisfying(값 -> assertThat(값).isEqualByComparingTo("-0.030000"));
    }

    /**
     * <b>부호가 바뀌는 값에서 비율은 무너진다.</b> 펀딩비가 +0.001 에서 −0.05 로 갔을 때
     * 비율은 −51 이고 그 수는 아무 뜻이 없다 — 배수를 버리고 분위를 쓴 것과 같은 문제다.
     * 그래서 펀딩비만 차이(%p)로 말한다.
     */
    @Test
    void 부호가_바뀌는_지표는_차이로_말한다() {
        assertThat(표본("0.001", "-0.05").changeOver(2, MetricKind.Change.DIFFERENCE))
                .hasValueSatisfying(값 -> assertThat(값).isEqualByComparingTo("-0.051"));
        assertThat(MetricKind.FUNDING_RATE.change()).isEqualTo(MetricKind.Change.DIFFERENCE);
        assertThat(MetricKind.OPEN_INTEREST.change()).isEqualTo(MetricKind.Change.RATIO);
    }

    /** <b>0 에서 출발한 비율은 내지 않는다.</b> 무한대를 큰 수로 적으면 화면이 급변으로 읽는다. */
    @Test
    void 영에서_출발하면_비율을_말할_수_없다() {
        assertThat(표본("0", "5").changeOver(2, MetricKind.Change.RATIO)).isEmpty();
        // 차이로는 말할 수 있다.
        assertThat(표본("0", "5").changeOver(2, MetricKind.Change.DIFFERENCE))
                .hasValueSatisfying(값 -> assertThat(값).isEqualByComparingTo("5"));
    }

    @Test
    void 표본이_창보다_적으면_변화를_말할_수_없다() {
        assertThat(표본("100").changeOver(2, MetricKind.Change.RATIO)).isEmpty();
        assertThat(표본().changeOver(2, MetricKind.Change.RATIO)).isEmpty();
    }

    @Test
    void 창은_최소_둘이어야_한다() {
        assertThatThrownBy(() -> 표본("1", "2").changeOver(1, MetricKind.Change.RATIO))
                .isInstanceOf(InvalidValueException.class);
    }

    /**
     * 창을 지표가 갖는 이유. <b>모든 지표에 "최근 30분" 을 쓰면 펀딩비에서 무의미해진다</b> —
     * 8시간마다 갱신되므로 30분 안에는 같은 값이 그대로 있다.
     */
    @Test
    void 창은_지표마다_다르다() {
        assertThat(MetricKind.FUNDING_RATE.recentWindow()).isEqualTo(3);
        assertThat(MetricKind.OPEN_INTEREST.recentWindow()).isEqualTo(6);
    }

    /** 가격은 지표 목록에 들지 않는다 — 나란히 놓기 위한 기준선이다. */
    @Test
    void 화면에_놓이는_지표에_가격은_없다() {
        assertThat(MetricKind.displayed()).doesNotContain(MetricKind.PRICE).hasSize(5);
    }

    private static MetricHistory 표본(String... values) {
        return new MetricHistory(Stream.of(values).map(BigDecimal::new).toList());
    }

    @Test
    void 표본이_비면_최근값도_없다() {
        assertThat(new MetricHistory(List.of()).latest()).isEmpty();
        assertThat(표본("1", "9").latest())
                .hasValueSatisfying(값 -> assertThat(값).isEqualByComparingTo("9"));
    }
}
