package com.coinwin.projection.api;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Percentage;
import com.coinwin.projection.domain.ProjectionSpec;
import com.coinwin.projection.domain.TradeFrequency;
import com.coinwin.projection.domain.TradingEdge;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * 시뮬레이션 조건. 두 엔드포인트가 같은 조건을 받으므로 한 타입으로 둔다.
 *
 * <p>진입가도 손절가도 받지 않는다. 이 모듈이 보는 것은 개별 거래가 아니라 <b>같은 규칙을
 * 반복했을 때 자산이 어떻게 움직이는가</b> 이고, 그것을 결정하는 것은 승률·손익비·리스크 비율뿐이다.
 */
@Schema(description = "복리 시뮬레이션 조건")
public record ProjectionSpecRequest(

        @Schema(description = "시작 자산 (USDT)", example = "800")
        BigDecimal initialCapital,

        @Schema(description = "승률. 45 는 100 번 중 45 번 이긴다는 뜻이다", example = "45")
        BigDecimal winRate,

        @Schema(description = "손익비. 이겼을 때 버는 폭이 졌을 때 잃는 폭의 몇 배인가", example = "2")
        BigDecimal riskRewardRatio,

        @Schema(description = "거래 한 건에 거는 현재 자산의 비율. 2 는 2% 를 뜻한다. "
                + "고정 금액이 아니라 비율이라 자산이 늘면 거는 돈도 늘어난다", example = "2")
        BigDecimal riskPerTrade,

        @Schema(description = "주당 거래 수", example = "2")
        Integer tradesPerWeek,

        @Schema(description = "시뮬레이션 기간 (주)", example = "50")
        Integer weeks) {

    ProjectionSpec toSpec() {
        return new ProjectionSpec(
                Money.of(initialCapital),
                new TradingEdge(
                        Percentage.of(winRate), riskRewardRatio, Percentage.of(riskPerTrade)),
                new TradeFrequency(
                        DomainValues.required(tradesPerWeek, "주당 거래 수"),
                        DomainValues.required(weeks, "기간(주)")));
    }
}
