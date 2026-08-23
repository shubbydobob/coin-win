package com.coinwin.market.domain;

import com.coinwin.common.domain.DomainValues;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 한 지표의 지금 값과, 그 값이 최근 표본에서 차지하는 위치.
 *
 * <p><b>위치가 비어 있을 수 있다.</b> 표본이 모자라면 말하지 않는다 — 손익비와 청산가에서 이미
 * 두 번 세운 규칙이다. 비었을 때 {@link #isOutlier()} 는 거짓이다. <b>모르는 것은 이상치가
 * 아니다</b> — 모른다는 이유로 경고를 띄우면 그 경고는 곧 배경이 된다.
 */
public record MetricOutlier(
        MetricKind kind,
        BigDecimal current,
        Optional<Percentile> position,
        Optional<BigDecimal> change,
        List<BigDecimal> samples) {

    public MetricOutlier {
        DomainValues.required(kind, "지표");
        DomainValues.required(current, "현재값");
        DomainValues.required(position, "표본 내 위치");
        DomainValues.required(change, "변화");
        samples = List.copyOf(DomainValues.required(samples, "표본"));
    }

    /** 표본에서 현재값의 위치를 재어 만든다. 표본이 모자라면 위치가 비어 있다. */
    public static MetricOutlier of(MetricKind kind, BigDecimal current, MetricHistory history) {
        DomainValues.required(kind, "지표");
        DomainValues.required(history, "표본");
        return new MetricOutlier(
                kind,
                current,
                history.positionOf(current, kind.minimumSamples()),
                history.changeOver(kind.recentWindow(), kind.change()),
                history.samples());
    }

    public int sampleCount() {
        return samples.size();
    }

    public boolean isOutlier() {
        return position.map(Percentile::isOutlier).orElse(false);
    }
}
