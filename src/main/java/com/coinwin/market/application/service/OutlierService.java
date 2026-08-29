package com.coinwin.market.application.service;

import com.coinwin.common.domain.ExternalDataUnavailableException;
import com.coinwin.market.application.port.in.LoadOutliersUseCase;
import com.coinwin.market.application.port.out.LoadMarketMetricsPort;
import com.coinwin.market.application.port.out.LoadMetricHistoryPort;
import com.coinwin.market.domain.MarketMetrics;
import com.coinwin.market.domain.MarketOutliers;
import com.coinwin.market.domain.MetricHistory;
import com.coinwin.market.domain.MetricKind;
import com.coinwin.market.domain.MetricOutlier;
import com.coinwin.market.domain.Symbol;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 지금 값과 지난 값을 맞대어 "평소와 얼마나 다른가" 를 낸다.
 *
 * <p>두 포트를 함께 쓰는 것이 이 서비스의 전부다. 판정은 전부 도메인이 한다 — 위치를 재는 것도
 * ({@code Percentile}), 표본이 모자라면 말하지 않는 것도({@code MetricHistory}), 무엇이
 * 이상치인지도({@code Percentile.isOutlier}) 여기에 없다.
 *
 * <p><b>관측 시각은 현재값 쪽에서 가져온다.</b> 이력의 마지막 시각이 아니다 — 사람이 보는 것은
 * "지금이 평소와 다른가" 이고, 그 '지금' 은 현재값이 관측된 순간이다.
 */
@Service
public class OutlierService implements LoadOutliersUseCase {

    private final LoadMarketMetricsPort metricsPort;
    private final LoadMetricHistoryPort historyPort;

    public OutlierService(LoadMarketMetricsPort metricsPort, LoadMetricHistoryPort historyPort) {
        this.metricsPort = metricsPort;
        this.historyPort = historyPort;
    }

    @Override
    public MarketOutliers outliers(Symbol symbol) {
        MarketMetrics now = metricsPort.metricsFor(symbol);
        return MarketOutliers.of(
                symbol,
                now.at(),
                List.of(
                        funding(symbol, now),
                        openInterest(symbol, now),
                        longShort(symbol, now),
                        taker(symbol),
                        topPosition(symbol)),
                price(symbol));
    }

    private MetricOutlier funding(Symbol symbol, MarketMetrics now) {
        MetricKind kind = MetricKind.FUNDING_RATE;
        return MetricOutlier.of(
                kind,
                now.fundingRate().value(),
                historyPort.fundingRates(symbol, kind.sampleSize()));
    }

    private MetricOutlier openInterest(Symbol symbol, MarketMetrics now) {
        MetricKind kind = MetricKind.OPEN_INTEREST;
        return MetricOutlier.of(
                kind,
                now.openInterest().value(),
                historyPort.openInterest(symbol, kind.sampleSize()));
    }

    private MetricOutlier longShort(Symbol symbol, MarketMetrics now) {
        MetricKind kind = MetricKind.LONG_SHORT_RATIO;
        return MetricOutlier.of(
                kind,
                now.longShortRatio(),
                historyPort.longShortRatios(symbol, kind.sampleSize()));
    }

    /**
     * 현재값을 이력의 마지막에서 가져온다.
     *
     * <p>{@code MarketMetrics} 에 이 셋이 없기 때문인데, 그것이 오히려 맞다 — 그 타입은 <b>한
     * 시점의 세 값</b>을 묶는 약속이고, 여기 넷을 더하면 그 묶음의 뜻이 흐려진다.
     */
    private MetricOutlier taker(Symbol symbol) {
        MetricKind kind = MetricKind.TAKER_RATIO;
        return fromHistory(kind, historyPort.takerRatios(symbol, kind.sampleSize()));
    }

    private MetricOutlier topPosition(Symbol symbol) {
        MetricKind kind = MetricKind.TOP_POSITION_RATIO;
        return fromHistory(kind, historyPort.topPositionRatios(symbol, kind.sampleSize()));
    }

    private MetricOutlier price(Symbol symbol) {
        MetricKind kind = MetricKind.PRICE;
        return fromHistory(kind, historyPort.prices(symbol, kind.sampleSize()));
    }

    private static MetricOutlier fromHistory(MetricKind kind, MetricHistory history) {
        return MetricOutlier.of(
                kind,
                history.latest().orElseThrow(() -> new ExternalDataUnavailableException(
                        kind + " 이력이 비어 있다", null)),
                history);
    }
}
