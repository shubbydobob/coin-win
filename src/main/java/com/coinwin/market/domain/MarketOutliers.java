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
public record MarketOutliers(
        Symbol symbol,
        Instant at,
        List<MetricOutlier> metrics,
        MetricOutlier price,
        List<MarketSituation> situations) {

    public MarketOutliers {
        DomainValues.required(symbol, "종목");
        DomainValues.required(at, "관측 시각");
        metrics = List.copyOf(DomainValues.required(metrics, "지표 목록"));
        DomainValues.required(price, "가격 기준선");
        situations = List.copyOf(DomainValues.required(situations, "상황"));
    }

    /**
     * 지표들과 <b>같은 구간의 가격</b>을 맞대어 만든다.
     *
     * <p>가격을 지표 목록이 아니라 따로 두는 이유는 그것이 <b>기준선</b>이기 때문이다.
     * 미결제약정 −3.2% 는 가격 +1.1% 옆에서만 뜻이 된다 — 포지션이 줄면서 가격이 올랐다면
     * 청산이고, 포지션이 줄면서 가격도 내렸다면 그냥 손을 턴 것이다.
     */
    public static MarketOutliers of(
            Symbol symbol, Instant at, List<MetricOutlier> metrics, MetricOutlier price) {
        return new MarketOutliers(symbol, at, metrics, price, MarketSituation.of(metrics, price));
    }

    /** 하나라도 양 끝 5% 안에 있는가. 화면이 이 블록을 강조할지 정하는 데 쓴다. */
    public boolean hasOutlier() {
        return metrics.stream().anyMatch(MetricOutlier::isOutlier);
    }
}
