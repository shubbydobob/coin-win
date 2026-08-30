package com.coinwin.account.adapter.in.web;

import com.coinwin.account.domain.ExchangePosition;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Percentage;
import com.coinwin.common.domain.Price;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * 대조의 거래소 쪽. 지금 실제로 열려 있는 것.
 *
 * <p><b>명목과 청산 거리를 서버가 낸다.</b> 화면에서 {@code 수량 × 가격} 을 하면 이 프로젝트가
 * 값 객체를 두는 이유(스케일과 반올림이 한 곳에)가 그 자리에서 무너진다 —
 * {@code docs/adr/020} 이 금지한 것이 정확히 이것이다.
 */
@Schema(description = "거래소가 말하는 지금 이 순간의 포지션")
public record ExchangeSideResponse(
        @Schema(description = "종목", example = "BTCUSDT")
        String symbol,

        @Schema(description = "거래소가 계산한 평단", example = "59500.00")
        BigDecimal entryPrice,

        @Schema(description = """
                거래소의 표시가. 청산이 트리거되고 미실현 손익이 계산되는 값이다.
                호가의 마지막 체결가와 다를 수 있고, 청산까지의 거리는 이것이 기준이다.""",
                example = "60120.00")
        BigDecimal markPrice,

        @Schema(description = "보유 수량. 언제나 양수이고 방향은 따로 있다",
                example = "0.10000000")
        BigDecimal quantity,

        @Schema(description = """
                거래소가 계산한 청산가. 우리 계산과 대조할 수 있는 유일한 값이다.
                거래소가 청산 지점을 말할 수 없으면 null 이다 — 0 은 청산가가 아니라 '없음'이다.""",
                nullable = true, example = "53765.06")
        BigDecimal liquidationPrice,

        @Schema(description = """
                표시가에서 청산가까지의 거리(%). 방향은 붙지 않는다 —
                롱이면 아래, 숏이면 위이고 그 방향은 direction 이 이미 말한다.
                거래소가 청산 지점을 말할 수 없으면 null 이다.""",
                nullable = true, example = "8.6560")
        BigDecimal liquidationDistancePercent,

        @Schema(description = """
                명목(USDT). 표시가 × 수량이다. **수량이 아니라 이것이 위험의 크기다** —
                0.13 BTC 라는 수는 얼마를 걸었는지를 말해 주지 않는다.""",
                example = "6012.00")
        BigDecimal notional,

        @Schema(description = "미실현 손익. 기록에는 없는 값이다 — 매 순간 달라지므로 기록의 대상이 아니다",
                example = "12.40")
        BigDecimal unrealizedPnl,

        @Schema(description = """
                이 포지션에 묶여 있는 개시증거금(USDT). 명목을 이것으로 나눈 것이 레버리지다.
                거래소가 말할 수 없으면 null 이다 — 0 원짜리 증거금은 배수를 무한대로 만든다.""",
                nullable = true, example = "280.72")
        BigDecimal margin,

        @Schema(description = """
                레버리지(배). **거래소가 주는 값이 아니라 명목 ÷ 증거금이다** —
                /fapi/v3/positionRisk 에는 leverage 필드가 없다(v2 에는 있었다).
                증거금이 없으면 null 이다. 0 이나 1 로 채우면 위험이 없다는 뜻으로 읽힌다.""",
                nullable = true, example = "44.00")
        BigDecimal leverage,

        @Schema(description = """
                청산에 닿으면 사라지는 돈(USDT). 수량 × |표시가 − 청산가| 이고,
                옆의 청산 거리와 같은 구간을 잰다. 청산가가 없으면 null 이다.""",
                nullable = true, example = "239.79")
        BigDecimal lossToLiquidation,

        @Schema(description = """
                그 손실이 증거금의 몇 %인가. **가격 1.94% 가 증거금 85% 일 수 있다** —
                거리만으로는 위험이 읽히지 않아 같은 사실을 돈 눈금으로도 적는다.
                **소수 둘이다** — Percentage 의 넷이 아니라, 뒤 두 자리가 표시가 1 센트에
                바뀌는 자리라 도메인이 깎아서 낸다. 100% 를 넘을 수 있고 그것은 깎지 않는다.
                청산가나 증거금이 없으면 null 이다.""",
                nullable = true, example = "83.97")
        BigDecimal marginAtRiskPercent) {

    static ExchangeSideResponse from(ExchangePosition position) {
        return new ExchangeSideResponse(
                position.symbol().value(),
                position.entryPrice().value(),
                position.markPrice().value(),
                position.quantity().value(),
                position.liquidationPrice().map(Price::value).orElse(null),
                position.liquidationDistance().map(Percentage::value).orElse(null),
                position.notional().value(),
                position.unrealizedPnl().value(),
                position.margin().map(Money::value).orElse(null),
                position.leverage().orElse(null),
                position.lossToLiquidation().map(Money::value).orElse(null),
                position.marginAtRisk().map(Percentage::value).orElse(null));
    }
}
