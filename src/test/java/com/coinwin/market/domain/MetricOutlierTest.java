package com.coinwin.market.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.InvalidValueException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** 한 지표의 지금 값과 표본 내 위치. */
class MetricOutlierTest {

    @Test
    void 표본이_충분하면_위치를_낸다() {
        MetricOutlier outlier =
                MetricOutlier.of(MetricKind.OPEN_INTEREST, new BigDecimal("50"), 표본(100));

        assertThat(outlier.position()).isPresent();
        assertThat(outlier.sampleCount()).isEqualTo(100);
    }

    /**
     * <b>모르는 것은 이상치가 아니다.</b> 표본이 모자라 위치를 못 냈는데 경고를 띄우면, 그
     * 경고는 "평소와 다르다" 가 아니라 "아직 모른다" 를 뜻하게 되고 곧 배경이 된다.
     */
    @Test
    void 표본이_모자라면_위치도_없고_이상치도_아니다() {
        MetricOutlier outlier =
                MetricOutlier.of(MetricKind.OPEN_INTEREST, new BigDecimal("50"), 표본(19));

        assertThat(outlier.position()).isEmpty();
        assertThat(outlier.isOutlier()).isFalse();
        assertThat(outlier.sampleCount()).isEqualTo(19);
    }

    @Test
    void 표본의_맨_위에_있으면_이상치다() {
        MetricOutlier outlier =
                MetricOutlier.of(MetricKind.OPEN_INTEREST, new BigDecimal("999"), 표본(30));

        assertThat(outlier.isOutlier()).isTrue();
    }

    @Test
    void 표본_한가운데는_이상치가_아니다() {
        MetricOutlier outlier =
                MetricOutlier.of(MetricKind.OPEN_INTEREST, new BigDecimal("15"), 표본(30));

        assertThat(outlier.isOutlier()).isFalse();
    }

    @Test
    void 지표마다_최소_표본이_다르다() {
        assertThat(MetricKind.FUNDING_RATE.minimumSamples()).isEqualTo(30);
        assertThat(MetricKind.OPEN_INTEREST.minimumSamples()).isEqualTo(20);
        assertThat(MetricKind.FUNDING_RATE.sampleSize()).isEqualTo(90);
    }

    @Test
    void 위치는_null_일_수_없다() {
        assertThatThrownBy(() -> new MetricOutlier(
                        MetricKind.FUNDING_RATE, BigDecimal.ONE, null, Optional.empty(),
                        CrowdedSide.LONG, Optional.empty(), List.of()))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    void 하나라도_이상치면_묶음이_이상치를_갖는다() {
        MetricOutlier 이상치 = MetricOutlier.of(MetricKind.OPEN_INTEREST, new BigDecimal("999"), 표본(30));
        MetricOutlier 보통 = MetricOutlier.of(MetricKind.OPEN_INTEREST, new BigDecimal("15"), 표본(30));
        Instant at = Instant.parse("2026-08-23T09:00:00Z");

        MetricOutlier 가격 = MetricOutlier.of(MetricKind.PRICE, new BigDecimal("15"), 표본(30));

        assertThat(MarketOutliers.of(Symbol.of("BTCUSDT"), at, List.of(보통, 이상치), 가격)
                        .hasOutlier())
                .isTrue();
        assertThat(MarketOutliers.of(Symbol.of("BTCUSDT"), at, List.of(보통), 가격).hasOutlier())
                .isFalse();
    }

    @Test
    void 위치를_직접_넘길_수도_있다() {
        MetricOutlier outlier = new MetricOutlier(
                MetricKind.FUNDING_RATE,
                BigDecimal.ONE,
                Optional.of(new Percentile(new BigDecimal("0.99"))),
                Optional.empty(),
                CrowdedSide.LONG,
                Optional.empty(),
                List.of(BigDecimal.ONE));

        assertThat(outlier.isOutlier()).isTrue();
    }

    private static MetricHistory 표본(int count) {
        return new MetricHistory(
                IntStream.rangeClosed(1, count).mapToObj(BigDecimal::valueOf).toList());
    }
}
