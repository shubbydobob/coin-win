package com.coinwin.projection.api;

import com.coinwin.common.domain.ExchangeRate;
import com.coinwin.common.domain.Money;
import com.coinwin.projection.domain.CompoundTarget;
import com.coinwin.projection.domain.RequiredEdge;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 목표를 그대로 지켰을 때의 자산과, 그 목표가 거래 한 건에 요구하는 것.
 *
 * <p>두 묶음이 함께 실린다. 앞의 넷은 <b>목표가 이루어진 세계</b>이고, 뒤의 다섯은 <b>그
 * 세계에 가려면 거래 한 건이 무엇을 해야 하는가</b> 다. 앞만 보면 복리 계산기가 늘 보여
 * 주는 기분 좋은 곡선 하나가 남고, <b>그 곡선에 붙은 값이 사라진다.</b>
 */
@Schema(description = "목표 복리 계산 결과", example = ProjectionApiExamples.COMPOUND_RESPONSE)
public record CompoundTargetResponse(

        @Schema(description = "월말마다의 자산. 첫 값은 거래 이전의 시작 자산이다")
        List<BigDecimal> equity,

        @Schema(description = "기간이 끝났을 때의 자산 (USDT)", example = "1436.69")
        BigDecimal finalEquity,

        @Schema(description = "비용을 내기 전에 번 금액 (USDT). 순이익 + 총 비용", example = "1366.57")
        BigDecimal grossProfit,

        @Schema(description = "기간 동안 늘어난 금액 (USDT). 번 돈에서 비용을 내고 남는 것",
                example = "636.69")
        BigDecimal totalProfit,

        @Schema(description = "기간 (개월). 요청에 실린 값을 그대로 되돌려준다 — 화면이 "
                + "점의 수를 세어 기간을 짐작하지 않게 한다", example = "12")
        int months,

        @Schema(description = "기간 전체 수익률 (%). 월 목표 × 개월 이 아니라 복리다",
                example = "79.5856")
        BigDecimal totalReturn,

        @Schema(description = "기간 동안 수수료와 슬리피지로 나가는 총액 (USDT). "
                + "시작 자산을 넘는 것은 흔한 일이다", example = "729.88")
        BigDecimal totalCost,

        @Schema(description = "시작 시점에 시장에 나가는 크기 (USDT). 수수료는 이쪽에 붙는다",
                example = "1600.00")
        BigDecimal notional,

        @Schema(description = "명목이 자산의 몇 배인가. 투입 비율 × 레버리지. "
                + "비용과 필요 가격 변동은 전부 이 하나로 결정된다", example = "2.00")
        BigDecimal effectiveLeverage,

        @Schema(description = "기간 전체의 거래 수", example = "240")
        int totalTrades,

        @Schema(description = "거래당 필요 순수익 (%). 자산 기준이며 비용을 낸 뒤에 남아야 하는 몫",
                example = "0.2442")
        BigDecimal netPerTrade,

        @Schema(description = "거래당 비용 (%). 자산 기준. 레버리지 × (수수료 + 슬리피지) × 왕복",
                example = "0.2800")
        BigDecimal costPerTrade,

        @Schema(description = "거래당 필요 총수익 (%). 순수익 + 비용", example = "0.5242")
        BigDecimal grossPerTrade,

        @Schema(description = "거래당 필요 가격 변동 (%). 차트에서 재는 폭은 이 수다",
                example = "0.2621")
        BigDecimal priceMovePerTrade,

        @Schema(description = "필요 총수익 중 비용이 가져가는 몫 (%). 레버리지를 올리면 필요한 "
                + "가격 변동은 작아지지만 이 몫은 커진다", example = "53.4147")
        BigDecimal costShare,

        @Schema(description = "같은 금액들을 원화로 옮긴 것. 환율을 얻지 못하면 null 이다 — "
                + "옛 환율이나 0 원으로 채우지 않는다", nullable = true)
        WonAmountsResponse won) {

    public CompoundTargetResponse {
        equity = List.copyOf(equity);
    }

    static CompoundTargetResponse from(CompoundTarget target, Optional<ExchangeRate> rate) {
        RequiredEdge edge = target.requiredEdge();
        return new CompoundTargetResponse(
                target.monthlyEquity().stream().map(Money::value).toList(),
                target.finalEquity().value(),
                target.grossProfit().value(),
                target.totalProfit().value(),
                target.months(),
                target.totalReturn().value(),
                target.totalCost().value(),
                target.notional().value(),
                target.cost().effectiveLeverage(),
                target.totalTrades(),
                edge.netPerTrade().value(),
                edge.costPerTrade().value(),
                edge.grossPerTrade().value(),
                edge.priceMove().value(),
                edge.costShare().value(),
                rate.map(present -> WonAmountsResponse.of(target, present)).orElse(null));
    }

    /**
     * 같은 결과를 원화로 옮긴 것.
     *
     * <p><b>화면이 곱하지 않게 하려고 서버가 낸다.</b> 환산은 반올림 정책을 가진 계산이고,
     * 그 정책이 자바와 타입스크립트 양쪽에 생기면 언젠가 한쪽만 바뀐다({@code docs/adr/020}).
     *
     * <p>환율과 그것을 잰 시각을 함께 싣는다. 어느 환율로 옮긴 값인지 말하지 않으면 사람은
     * 언제나 지금 환율로 읽는다.
     */
    @Schema(description = "업비트 원화 시세로 환산한 금액")
    public record WonAmountsResponse(

            @Schema(description = "USDT 하나가 몇 원인가", example = "1370.00")
            BigDecimal wonPerUsdt,

            @Schema(description = "환율을 잰 시각", example = "2026-08-23T15:04:04Z")
            Instant observedAt,

            @Schema(description = "기간이 끝났을 때의 자산 (원)", example = "1968265")
            BigDecimal finalEquity,

            @Schema(description = "기간 동안 늘어난 금액 (원)", example = "872265")
            BigDecimal totalProfit,

            @Schema(description = "기간 동안 수수료와 슬리피지로 나가는 총액 (원)",
                    example = "999936")
            BigDecimal totalCost,

            @Schema(description = "시작 시점에 시장에 나가는 크기 (원)", example = "2192000")
            BigDecimal notional) {

        static WonAmountsResponse of(CompoundTarget target, ExchangeRate rate) {
            return new WonAmountsResponse(
                    rate.wonPerUsdt(),
                    rate.observedAt(),
                    won(rate, target.finalEquity()),
                    won(rate, target.totalProfit()),
                    won(rate, target.totalCost()),
                    won(rate, target.notional()));
        }

        private static BigDecimal won(ExchangeRate rate, Money usdt) {
            return rate.convert(usdt).value();
        }
    }
}
