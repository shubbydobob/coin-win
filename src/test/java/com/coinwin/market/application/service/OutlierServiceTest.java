package com.coinwin.market.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.coinwin.common.domain.Quantity;
import com.coinwin.market.adapter.out.memory.InMemoryMetricHistoryAdapter;
import com.coinwin.market.domain.FundingRate;
import com.coinwin.market.domain.MarketMetrics;
import com.coinwin.market.domain.MarketOutliers;
import com.coinwin.market.domain.MetricHistory;
import com.coinwin.market.domain.MetricKind;
import com.coinwin.market.domain.Symbol;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 지금 값과 지난 값을 맞대는 조율. 판정은 전부 도메인이 한다. */
class OutlierServiceTest {

    private static final Symbol SYMBOL = Symbol.of("BTCUSDT");
    private static final Instant AT = Instant.parse("2026-08-23T09:00:00Z");

    @Test
    void 세_지표를_한_시각으로_묶는다() {
        MarketOutliers outliers = 서비스().outliers(SYMBOL);

        assertThat(outliers.at()).isEqualTo(AT);
        assertThat(outliers.metrics()).extracting(m -> m.kind())
                .containsExactly(
                        MetricKind.FUNDING_RATE,
                        MetricKind.OPEN_INTEREST,
                        MetricKind.LONG_SHORT_RATIO);
    }

    /** 관측 시각은 <b>현재값</b> 쪽에서 온다. 이력의 마지막 시각이 아니다. */
    @Test
    void 관측_시각은_현재값의_시각이다() {
        assertThat(서비스().outliers(SYMBOL).at()).isEqualTo(AT);
    }

    @Test
    void 이력이_모자란_지표는_위치를_내지_않는다() {
        InMemoryMetricHistoryAdapter history = InMemoryMetricHistoryAdapter.withSample(SYMBOL);
        history.putFunding(SYMBOL, new MetricHistory(List.of(BigDecimal.ONE, BigDecimal.TEN)));

        MarketOutliers outliers =
                new OutlierService(symbol -> 지표(), history).outliers(SYMBOL);

        assertThat(outliers.metrics().getFirst().position()).isEmpty();
        assertThat(outliers.metrics().get(1).position()).isPresent();
    }

    private static OutlierService 서비스() {
        return new OutlierService(symbol -> 지표(), InMemoryMetricHistoryAdapter.withSample(SYMBOL));
    }

    private static MarketMetrics 지표() {
        return new MarketMetrics(
                SYMBOL, AT, FundingRate.ofPercent("0.01"), Quantity.of("107134"),
                new BigDecimal("0.98"));
    }
}
