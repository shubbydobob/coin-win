package com.coinwin.market.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * 지금 값이 어느 쪽 진영인가.
 *
 * <p>화면이 "상위 96.7%" 까지만 말하고 그것이 롱 쪽인지 숏 쪽인지를 말하지 않던 자리다.
 * 여기서 못 박는 것은 <b>진영까지가 사실이고 우열은 아니라는 선</b>이다.
 */
class CrowdedSideTest {

    private static final Instant AT = Instant.parse("2026-08-23T09:00:00Z");

    @Test
    void 펀딩비가_양수면_롱이_비용을_내는_쪽이다() {
        assertThat(CrowdedSide.of(MetricKind.FUNDING_RATE, new BigDecimal("0.031")))
                .isEqualTo(CrowdedSide.LONG);
    }

    @Test
    void 펀딩비가_음수면_숏이_비용을_내는_쪽이다() {
        assertThat(CrowdedSide.of(MetricKind.FUNDING_RATE, new BigDecimal("-0.004")))
                .isEqualTo(CrowdedSide.SHORT);
    }

    /** 펀딩비의 중립점은 0 이고 비율 지표의 중립점은 1 이다 — 같은 0.5 가 반대 진영이 된다. */
    @Test
    void 비율_지표의_중립점은_0_이_아니라_1_이다() {
        assertThat(CrowdedSide.of(MetricKind.FUNDING_RATE, new BigDecimal("0.5")))
                .isEqualTo(CrowdedSide.LONG);
        assertThat(CrowdedSide.of(MetricKind.LONG_SHORT_RATIO, new BigDecimal("0.5")))
                .isEqualTo(CrowdedSide.SHORT);
    }

    @Test
    void 상위_계정_포지션과_테이커도_1_을_기준으로_갈린다() {
        assertThat(CrowdedSide.of(MetricKind.TOP_POSITION_RATIO, new BigDecimal("0.84")))
                .isEqualTo(CrowdedSide.SHORT);
        assertThat(CrowdedSide.of(MetricKind.TAKER_RATIO, new BigDecimal("1.1866")))
                .isEqualTo(CrowdedSide.LONG);
    }

    @Test
    void 정확히_중립점이면_어느_쪽도_아니다() {
        assertThat(CrowdedSide.of(MetricKind.LONG_SHORT_RATIO, BigDecimal.ONE))
                .isEqualTo(CrowdedSide.BALANCED);
        assertThat(CrowdedSide.of(MetricKind.FUNDING_RATE, new BigDecimal("0.000")))
                .isEqualTo(CrowdedSide.BALANCED);
    }

    /**
     * <b>축이 없는 것과 가운데 있는 것은 다르다.</b> 미결제약정은 크기이지 방향이 아니므로
     * "몇이면 롱 쪽인가" 라는 질문 자체가 성립하지 않는다. 이것을 {@code BALANCED} 로 섞으면
     * "방향 있는 지표 몇 중 몇이 롱 쪽인가" 를 셀 수 없게 된다.
     */
    @Test
    void 미결제약정과_가격은_축_자체가_없다() {
        assertThat(CrowdedSide.of(MetricKind.OPEN_INTEREST, new BigDecimal("84102")))
                .isEqualTo(CrowdedSide.NONE);
        assertThat(CrowdedSide.of(MetricKind.PRICE, new BigDecimal("118240")))
                .isEqualTo(CrowdedSide.NONE);
    }

    /**
     * <b>진영은 표본과 무관하다.</b> 펀딩비가 양수면 롱이 숏에게 낸다는 것은 정의이므로
     * 표본이 하나도 없어도 성립한다. 위치와 한 칸에 묶었다면 여기서 함께 사라졌을 것이다.
     */
    @Test
    void 표본이_모자라도_진영은_말할_수_있다() {
        MetricOutlier outlier = MetricOutlier.of(
                MetricKind.FUNDING_RATE, new BigDecimal("0.031"), new MetricHistory(List.of()));

        assertThat(outlier.position()).isEmpty();
        assertThat(outlier.neutralPosition()).isEmpty();
        assertThat(outlier.side()).isEqualTo(CrowdedSide.LONG);
    }

    /**
     * 중립점 위치는 현재값 위치와 <b>같은 방식으로</b> 잰다. 눈금을 순위로 그려 놓고 가운데만
     * 선형으로 찍으면 점과 선이 다른 좌표계에 있게 된다.
     */
    @Test
    void 중립점도_현재값과_같은_눈금_위에_찍힌다() {
        // 0.5 ~ 1.5 를 0.05 씩 21개. 1.0 은 정확히 절반 지점이다.
        List<BigDecimal> 표본 = IntStream.rangeClosed(0, 20)
                .mapToObj(step -> new BigDecimal("0.50").add(new BigDecimal("0.05").multiply(
                        BigDecimal.valueOf(step))))
                .toList();

        MetricOutlier outlier = MetricOutlier.of(
                MetricKind.LONG_SHORT_RATIO, new BigDecimal("1.50"), new MetricHistory(표본));

        assertThat(outlier.neutralPosition()).get()
                .extracting(Percentile::value)
                .isEqualTo(new BigDecimal("0.5000"));
        assertThat(outlier.position()).get()
                .extracting(Percentile::value)
                .isEqualTo(new BigDecimal("0.9762"));
    }

    /**
     * 표본 내내 한쪽이었다면 가운데선이 눈금 끝에 붙는다. <b>그것도 사실이다</b> —
     * 지금만 롱 쪽인 것과 30일 내내 롱 쪽이었던 것은 다른 상황이고, 붙은 선이 그것을 그린다.
     */
    @Test
    void 표본이_전부_한쪽이면_가운데선이_눈금_끝에_붙는다() {
        List<BigDecimal> 표본 = IntStream.rangeClosed(1, 30)
                .mapToObj(step -> new BigDecimal("1.10"))
                .toList();

        MetricOutlier outlier = MetricOutlier.of(
                MetricKind.LONG_SHORT_RATIO, new BigDecimal("1.10"), new MetricHistory(표본));

        assertThat(outlier.neutralPosition()).get()
                .extracting(Percentile::value)
                .isEqualTo(new BigDecimal("0.0000"));
    }

    /**
     * <b>셈이지 판정이 아니다.</b> 두 수를 하나로 합치지 않은 것이 그 선이다 — 합치려면
     * 지표에 가중치를 줘야 하고 그 가중치는 검증할 방법이 없다({@code docs/adr/021}).
     */
    @Test
    void 붐비는_쪽을_세되_합쳐서_우열을_내지_않는다() {
        MarketOutliers outliers = MarketOutliers.of(
                Symbol.of("BTCUSDT"),
                AT,
                List.of(
                        지표(MetricKind.FUNDING_RATE, "0.031"),
                        지표(MetricKind.LONG_SHORT_RATIO, "1.04"),
                        지표(MetricKind.TAKER_RATIO, "1.18"),
                        지표(MetricKind.TOP_POSITION_RATIO, "0.84"),
                        지표(MetricKind.OPEN_INTEREST, "84102")),
                지표(MetricKind.PRICE, "118240"));

        assertThat(outliers.crowdedLong()).isEqualTo(3);
        assertThat(outliers.crowdedShort()).isEqualTo(1);
    }

    /** 축이 없는 지표는 어느 쪽에도 안 들어간다 — 그래서 합이 지표 수와 다를 수 있다. */
    @Test
    void 축이_없는_지표는_어느_쪽으로도_세지_않는다() {
        MarketOutliers outliers = MarketOutliers.of(
                Symbol.of("BTCUSDT"),
                AT,
                List.of(지표(MetricKind.OPEN_INTEREST, "84102")),
                지표(MetricKind.PRICE, "118240"));

        assertThat(outliers.crowdedLong()).isZero();
        assertThat(outliers.crowdedShort()).isZero();
    }

    private static MetricOutlier 지표(MetricKind kind, String current) {
        return MetricOutlier.of(kind, new BigDecimal(current), new MetricHistory(List.of()));
    }
}
