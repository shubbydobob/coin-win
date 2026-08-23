package com.coinwin.market.adapter.in.web;

import com.coinwin.market.domain.MarketOutliers;
import com.coinwin.market.domain.MarketSituation;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * 세 지표가 각각 평소와 얼마나 다른가.
 *
 * <p>한 시각으로 묶는 이유는 {@link MarketMetricsResponse} 와 같다 — 따로 내면 서로 다른
 * 순간의 값을 나란히 놓고 판단하게 된다.
 *
 * <p><b>무엇을 하라고 말하지 않는다.</b> 펀딩비가 상위 2% 라는 것은 사실이고, 그것이 매수
 * 신호인지 매도 신호인지는 이 프로젝트가 답하지 않기로 한 질문이다({@code scope.md}).
 */
@Schema(description = "세 지표의 평소 대비 위치", example = MarketApiExamples.OUTLIERS_RESPONSE)
public record MetricOutliersResponse(

        @Schema(description = "종목", example = "BTCUSDT")
        String symbol,

        @Schema(description = "현재값을 관측한 시각", example = "2026-08-23T09:00:00Z")
        Instant at,

        @Schema(description = "하나라도 양 끝 5% 안에 있는가", example = "true")
        boolean hasOutlier,

        @Schema(description = "지표별 결과. 펀딩비 · 미결제약정 · 롱숏비율 · 테이커 · 상위계정 순이다")
        List<MetricOutlierResponse> metrics,

        @Schema(description = "같은 창의 가격. **지표가 아니라 기준선이다** — "
                + "미결제약정 −3.2% 는 가격 +1.1% 옆에서만 뜻이 된다")
        MetricOutlierResponse price,

        @Schema(description = "지금 기계적으로 성립하는 사실들. **비어 있는 것이 정상이다.** "
                + "무엇을 하라고 말하지 않고 방향도 말하지 않는다")
        List<String> situations) {

    public MetricOutliersResponse {
        metrics = List.copyOf(metrics);
        situations = List.copyOf(situations);
    }

    static MetricOutliersResponse from(MarketOutliers outliers) {
        return new MetricOutliersResponse(
                outliers.symbol().value(),
                outliers.at(),
                outliers.hasOutlier(),
                outliers.metrics().stream().map(MetricOutlierResponse::from).toList(),
                MetricOutlierResponse.from(outliers.price()),
                outliers.situations().stream().map(MarketSituation::description).toList());
    }
}
