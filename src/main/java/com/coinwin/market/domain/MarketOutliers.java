package com.coinwin.market.domain;

import com.coinwin.common.domain.DomainValues;
import java.time.Instant;
import java.util.List;

/**
 * 한 시점의 세 지표가 각각 평소와 얼마나 다른가.
 *
 * <p>셋을 한 시각으로 묶는 이유는 {@link MarketMetrics} 와 같다 — 따로 내면 서로 다른 순간의
 * 값을 나란히 놓고 판단하게 된다.
 *
 * <p><b>이 타입은 무엇을 하라고 말하지 않는다.</b> 펀딩비가 상위 2% 라는 것은 사실이고, 그것이
 * 매수 신호인지 매도 신호인지는 이 프로젝트가 답하지 않기로 한 질문이다({@code scope.md}).
 */
public record MarketOutliers(Symbol symbol, Instant at, List<MetricOutlier> metrics) {

    public MarketOutliers {
        DomainValues.required(symbol, "종목");
        DomainValues.required(at, "관측 시각");
        metrics = List.copyOf(DomainValues.required(metrics, "지표 목록"));
    }

    /** 하나라도 양 끝 5% 안에 있는가. 화면이 이 블록을 강조할지 정하는 데 쓴다. */
    public boolean hasOutlier() {
        return metrics.stream().anyMatch(MetricOutlier::isOutlier);
    }
}
