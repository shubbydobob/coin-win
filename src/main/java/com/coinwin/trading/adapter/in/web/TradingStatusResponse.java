package com.coinwin.trading.adapter.in.web;

import com.coinwin.trading.adapter.out.TradingProperties;
import com.coinwin.trading.domain.RiskLimits;
import com.coinwin.trading.domain.TradingStrategy;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * 봇이 지금 어떤 상태인가.
 *
 * <p><b>모드가 첫 칸이다.</b> 어느 모드로 돌고 있는지 모르는 상태가 존재하면 안 된다 —
 * 장부인 줄 알았는데 실계좌인 것이 이 기능에서 가장 나쁜 고장이다.
 */
@Schema(description = "봇의 지금 상태와 한계")
public record TradingStatusResponse(
        @Schema(description = "루프가 도는가. 꺼져 있으면 열린 포지션도 건드리지 않는다",
                example = "false")
        boolean enabled,

        @Schema(description = """
                주문이 어디로 가는가. PAPER 는 장부에만 적고 돈이 들지 않는다.
                지금 이 저장소에는 장부 브로커밖에 없어 LIVE 로 뜰 수 없다.""",
                example = "PAPER", allowableValues = {"PAPER", "TESTNET", "LIVE"})
        String mode,

        @Schema(description = "이 모드에서 잃을 수 있는 것이 진짜 돈인가", example = "false")
        boolean realMoney,

        @Schema(description = "무엇이 판단하는가", example = "가만히 있기")
        String strategy,

        @Schema(description = "얼마나 자주 깨어나는가(ISO-8601). 주기는 전략의 일부인데 "
                + "전략이 없어 자리표시자다", example = "PT1M")
        String cycle,

        @Schema(description = "한 포지션 명목이 계좌의 몇 배까지인가", example = "2")
        BigDecimal maxNotionalMultiple,

        @Schema(description = "동시에 열 수 있는 포지션 수", example = "1")
        int maxConcurrentPositions,

        @Schema(description = "하루에 잃을 수 있는 계좌 대비 비율(%)", example = "5.0000")
        BigDecimal maxDailyLossPercent,

        @Schema(description = "넘으면 사람이 켜야 다시 도는 누적 손실 비율(%)",
                example = "20.0000")
        BigDecimal maxTotalLossPercent) {

    public static TradingStatusResponse of(
            TradingProperties properties, TradingStrategy strategy, RiskLimits limits) {
        return new TradingStatusResponse(
                properties.enabled(),
                properties.mode().name(),
                properties.mode().movesRealMoney(),
                strategy.name(),
                properties.cycle().toString(),
                limits.maxNotionalMultiple(),
                limits.maxConcurrentPositions(),
                limits.maxDailyLoss().value(),
                limits.maxTotalLoss().value());
    }
}
