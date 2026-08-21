package com.coinwin.backtest.api;

import com.coinwin.backtest.domain.AccountSettings;
import com.coinwin.backtest.domain.BacktestSpec;
import com.coinwin.backtest.domain.CapitalMode;
import com.coinwin.backtest.domain.CostModel;
import com.coinwin.backtest.domain.EntryRules;
import com.coinwin.backtest.domain.StrategySettings;
import com.coinwin.backtest.domain.ZoneSettings;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Percentage;
import com.coinwin.market.domain.CandleInterval;
import com.coinwin.market.domain.CandleQuery;
import com.coinwin.market.domain.Symbol;
import com.coinwin.market.domain.TimeRange;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;

/** 백테스트 한 번의 입력. 이 요청이 같으면 응답도 같다. */
@Schema(description = "지지·저항 반전 백테스트 실행 조건. 같은 요청은 언제나 같은 결과를 낸다")
public record RunBacktestRequest(
        @Schema(description = "대상 종목. 저장된 캔들이 있어야 한다", example = "BTCUSDT")
        String symbol,

        @Schema(description = "캔들 주기. 대 설정이 ATR 배수라 어느 주기든 같은 값으로 돈다",
                example = "4h")
        String interval,

        @Schema(description = "구간 시작. 워밍업(일목 77봉)만큼 앞의 캔들도 필요하다",
                example = "2026-01-01T00:00:00Z")
        Instant from,

        @Schema(description = "구간 끝. 이 시각의 캔들은 포함하지 않는다 (반열림)",
                example = "2026-06-01T00:00:00Z")
        Instant to,

        @Schema(description = "대 설정")
        ZoneSettingsRequest zones,

        @Schema(description = "진입 규칙")
        EntryRulesRequest rules,

        @Schema(description = "계좌 설정")
        AccountRequest account,

        @Schema(description = "비용. 생략하면 바이낸스 기본값(maker 0.02% / taker 0.05%)")
        CostsRequest costs) {

    public BacktestSpec toSpec() {
        return new BacktestSpec(
                new CandleQuery(new Symbol(symbol), CandleInterval.ofCode(interval),
                        new TimeRange(from, to)),
                new StrategySettings(zones.toSettings(), rules.toRules()),
                account.toSettings(),
                costs == null ? CostModel.binanceDefaults() : costs.toModel());
    }

    @Schema(description = "대를 만드는 설정. 전부 봉 수이거나 ATR 배수라 주기에 종속되지 않는다")
    public record ZoneSettingsRequest(
            @Schema(description = "스윙 극값 판정에 쓸 좌우 봉 수. 확정 지연도 이 값이다",
                    example = "5")
            int pivotLookback,

            @Schema(description = "같은 대로 묶을 최대 간격. ATR 의 몇 배인가", example = "0.5")
            BigDecimal clusterMultiple,

            @Schema(description = "대로 채택할 최소 터치 수. 피벗 하나는 선이지 대가 아니다",
                    example = "2")
            int minTouches,

            @Schema(description = "ATR 평활 구간", example = "14")
            int atrPeriod) {

        ZoneSettings toSettings() {
            return new ZoneSettings(pivotLookback, clusterMultiple, minTouches, atrPeriod);
        }
    }

    @Schema(description = "신호를 계획으로 바꾸는 규칙")
    public record EntryRulesRequest(
            @Schema(description = "손절을 대 원단에서 얼마나 더 미는가. ATR 의 몇 배", example = "1.0")
            BigDecimal stopBufferMultiple,

            @Schema(description = "이 손익비에 못 미치는 계획은 진입하지 않는다", example = "1.5")
            BigDecimal minRiskReward,

            @Schema(description = "일목·볼린저를 진입 게이트로 쓸 것인가. 끄면 판정을 기록만 한다",
                    example = "true")
            boolean indicatorFilter) {

        EntryRules toRules() {
            return new EntryRules(stopBufferMultiple, minRiskReward, indicatorFilter);
        }
    }

    @Schema(description = "얼마를 걸 것인가")
    public record AccountRequest(
            @Schema(description = "시작 자본 (USDT)", example = "800")
            BigDecimal initialCapital,

            @Schema(description = "거래당 걸 비율. 손실 크기는 손절가가 아니라 이 값이 정한다",
                    example = "2")
            BigDecimal riskPercent,

            @Schema(description = "레버리지. 증거금과 청산가에만 영향을 준다", example = "10")
            int leverage,

            @Schema(description = "FIXED 는 매 거래 초기 자본 기준, COMPOUND 는 직전까지의 자산 기준",
                    example = "COMPOUND")
            CapitalMode capitalMode) {

        AccountSettings toSettings() {
            return new AccountSettings(Money.of(initialCapital), Percentage.of(riskPercent),
                    leverage, capitalMode);
        }
    }

    @Schema(description = "수수료와 슬리피지. 진입은 maker, 청산은 taker + 슬리피지다")
    public record CostsRequest(
            @Schema(description = "진입 수수료율 (%)", example = "0.02")
            BigDecimal makerFee,

            @Schema(description = "청산 수수료율 (%)", example = "0.05")
            BigDecimal takerFee,

            @Schema(description = "청산 체결가가 불리한 쪽으로 밀리는 비율 (%)", example = "0.02")
            BigDecimal slippage) {

        CostModel toModel() {
            return new CostModel(Percentage.of(makerFee), Percentage.of(takerFee),
                    Percentage.of(slippage));
        }
    }
}
