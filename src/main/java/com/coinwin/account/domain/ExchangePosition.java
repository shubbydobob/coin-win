package com.coinwin.account.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Percentage;
import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import com.coinwin.market.domain.Symbol;
import com.coinwin.position.domain.Direction;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Optional;

/**
 * 거래소가 말하는 <b>지금 이 순간</b>의 포지션.
 *
 * <p>기록({@code journal}) 과 다른 종류의 사실이다. 기록은 <b>내가 무엇을 하려 했는지</b>를
 * 알고, 이것은 <b>지금 무엇이 열려 있는지</b>를 안다. 둘 다 필요하고 어느 쪽도 다른 쪽을
 * 대체하지 못한다 — 그래서 대조한다.
 *
 * <p><b>관측 시각을 함께 담는다.</b> 이 값은 다음 순간이면 달라지고, 언제 본 것인지 없이
 * 화면에 놓으면 사람이 그것을 현재로 읽는다. 기록에는 없는 필드인 이유이기도 하다 —
 * 기록은 이미 일어난 일이라 다시 관측되지 않는다.
 *
 * <p><b>수량은 언제나 양수다.</b> 방향은 {@link Direction} 이 들고 있다. 거래소는 숏을 음수
 * 수량으로 주지만, 부호와 방향이라는 두 표현이 한 객체에 함께 있으면 둘이 어긋날 수 있다.
 * 변환은 어댑터에서 끝낸다.
 *
 * @param symbol 종목. 거래소는 계좌의 모든 종목을 돌려주므로 거르는 데 쓴다
 * @param direction 롱 / 숏
 * @param quantity 보유 수량. 양수
 * @param entryPrice 거래소가 계산한 평단
 * @param markPrice 거래소의 표시가. <b>청산이 트리거되는 값이고 미실현 손익의 근거</b>이므로
 *     "청산까지 얼마나 남았나" 는 마지막 체결가가 아니라 이것으로 재야 한다. 같은 응답에서
 *     오므로 포지션과 정확히 같은 순간의 값이다
 * @param liquidationPrice 거래소가 계산한 청산가. 우리 계산과 대조할 수 있는 유일한 값이다.
 *     <b>비어 있을 수 있다</b> — 거래소는 청산가를 말할 수 없을 때 {@code "0"} 을 준다. 그것을
 *     0 원짜리 청산가로 담으면 화면이 "곧 청산된다" 는 뜻으로 읽는다. 손익비를
 *     {@code Optional} 로 둔 것과 같은 규칙이다
 * @param unrealizedPnl 미실현 손익. 음수일 수 있다
 * @param margin 이 포지션에 묶여 있는 개시증거금. <b>비어 있을 수 있다</b> — 거래소가 주지
 *     않거나 0 을 주면 배수를 말할 수 없고, 그때 0 원짜리 증거금으로 담으면 배수가 무한대가
 *     된다. {@code liquidationPrice} 와 같은 규칙이다
 * @param observedAt 이 값을 읽은 시각
 */
public record ExchangePosition(
        Symbol symbol,
        Direction direction,
        Quantity quantity,
        Price entryPrice,
        Price markPrice,
        Optional<Price> liquidationPrice,
        Money unrealizedPnl,
        Optional<Money> margin,
        Instant observedAt) {

    /** 배수의 스케일. 무차원이라 {@code TradingCost} 의 실효 배율과 같은 자릿수를 쓴다. */
    private static final int LEVERAGE_SCALE = 2;

    /**
     * 증거금 대비 위험의 스케일. <b>{@code Percentage} 의 넷이 아니라 둘이다.</b>
     *
     * <p>이 수는 "내 돈이 얼마나 걸려 있나" 를 읽는 것이지 정밀도가 의미를 갖는 값이 아니다 —
     * {@code 83.9741%} 의 뒤 두 자리는 표시가가 1 센트만 움직여도 바뀐다. <b>줄이는 일을 여기서
     * 한다</b>: 프론트의 표시 모듈은 "스케일을 줄여서 표시하지 않는다" 는 규칙을 갖고 있어서,
     * 화면에서 깎으면 그쪽이 반올림 정책을 갖게 되고 두 곳이 갈라질 수 있다.
     */
    private static final int MARGIN_RISK_SCALE = 2;

    public ExchangePosition {
        DomainValues.required(symbol, "종목");
        DomainValues.required(direction, "방향");
        DomainValues.required(quantity, "보유 수량");
        DomainValues.required(entryPrice, "평단");
        DomainValues.required(markPrice, "표시가");
        DomainValues.required(liquidationPrice, "청산가");
        positiveIfPresent(liquidationPrice.map(Price::value), "청산가");
        DomainValues.required(unrealizedPnl, "미실현 손익");
        DomainValues.required(margin, "증거금");
        positiveIfPresent(margin.map(Money::value), "증거금");
        DomainValues.required(observedAt, "관측 시각");
        if (quantity.value().signum() <= 0) {
            throw new InvalidAccountDataException(
                    "포지션 수량은 0 보다 커야 한다: " + quantity.value().toPlainString());
        }
    }

    /**
     * 있다면 0 보다 커야 한다. <b>없는 것과 0 은 다른 사실이다.</b>
     *
     * <p>청산가와 증거금이 같은 규칙을 쓴다 — 거래소는 말할 수 없는 값을 {@code "0"} 으로
     * 주는데, 그것을 그대로 담으면 청산가는 "임박" 으로 증거금은 "무한대 배수" 로 읽힌다.
     */
    private static void positiveIfPresent(Optional<java.math.BigDecimal> value, String label) {
        if (value.isPresent() && value.get().signum() <= 0) {
            throw new InvalidAccountDataException(
                    label + " — 있다면 0 보다 커야 한다. 없는 것과 0 은 다른 사실이다");
        }
    }

    /**
     * 명목. <b>수량이 아니라 이것이 위험의 크기다.</b>
     *
     * <p>이 프로젝트를 만든 손실이 정확히 이 수를 몰라서 났다 — 64,000 숏에 명목 9,142 는
     * 원금 2,900 의 3.15배였고, 가격이 22% 오르자 원금의 69% 가 사라졌다. 0.13 BTC 라는 수는
     * 그 사실을 말해 주지 않는다({@code docs/spec/market-watch.md} § 0).
     */
    public Money notional() {
        return quantity.times(markPrice.asAmount());
    }

    /**
     * 표시가에서 청산가까지의 거리(%). 거래소가 청산 지점을 말하지 않으면 비어 있다.
     *
     * <p><b>방향을 붙이지 않는다.</b> 청산가는 롱이면 아래, 숏이면 위이고 그 방향은
     * {@link #direction()} 이 이미 말한다. 부호를 여기 또 실으면 같은 사실이 두 곳에 생긴다.
     *
     * <p>가격이 아니라 <b>비율</b>인 이유는 84,273 이라는 수만으로는 가까운지 먼지를 알 수
     * 없기 때문이다. 8.66% 는 그 자체로 읽힌다.
     */
    public Optional<Percentage> liquidationDistance() {
        return liquidationPrice.map(
                liquidation -> markPrice.absoluteDifference(liquidation)
                        .percentOf(markPrice.asAmount()));
    }

    /**
     * 레버리지. <b>거래소가 주는 값이 아니라 나누어 얻는 값이다.</b>
     *
     * <p>{@code /fapi/v3/positionRisk} 에는 {@code leverage} 필드가 없다(v2 에는 있었다).
     * 대신 개시증거금이 오고, 그것은 정의상 {@code 명목 ÷ 배수}이므로 되돌리면 배수가 나온다.
     *
     * <p><b>증거금과 나란히 놓아야 이 수가 검산된다.</b> 화면에 배수만 있으면 사람이 그것을
     * 설정값으로 읽고 그만이지만, 명목·증거금·배수 셋이 함께 있으면 곱셈이 닫힌다 —
     * 복리 화면에서 번 돈·낸 돈·남는 돈을 세 줄로 쌓은 것과 같은 이유다.
     *
     * <p>증거금을 말할 수 없으면 배수도 없다. 그 자리를 0 이나 1배로 채우면 <b>위험이 없다는
     * 뜻으로 읽힌다</b> — 이 값이 가리키는 것이 정확히 위험의 크기이므로 가장 나쁜 거짓말이다.
     */
    public Optional<BigDecimal> leverage() {
        return margin.map(posted -> DomainValues.scaled(
                notional().value().divide(posted.value(), LEVERAGE_SCALE, RoundingMode.HALF_UP),
                LEVERAGE_SCALE, "레버리지"));
    }

    /**
     * 청산에 닿으면 사라지는 돈. {@code 수량 × |표시가 − 청산가|}.
     *
     * <p><b>가격 거리만으로는 위험이 읽히지 않는다.</b> "1.94% 남았다" 는 작게 읽히는데
     * 44배에서 그 1.94% 는 증거금의 85% 다. 같은 사실을 두 눈금으로 적어야 그 간극이 보인다.
     *
     * <p><b>기준을 옆의 퍼센트와 맞춘다</b> — 둘 다 <b>표시가에서 청산가까지</b>다. 평단에서
     * 재면 미실현 손익만큼 어긋나고, 그러면 한 줄 안의 두 수가 다른 구간을 말하게 된다.
     */
    public Optional<Money> lossToLiquidation() {
        return liquidationPrice.map(
                liquidation -> quantity.times(markPrice.absoluteDifference(liquidation)));
    }

    /**
     * 그 손실이 증거금의 몇 %인가. <b>청산가와 증거금이 둘 다 있어야 말할 수 있다.</b>
     *
     * <p>100% 를 넘을 수 있고 그것을 깎지 않는다 — 넘는다는 것은 청산 전에 증거금이 먼저
     * 바닥난다는 뜻이고, 그 사실을 100% 로 눌러 적으면 "딱 맞게 버틴다" 로 읽힌다.
     */
    public Optional<Percentage> marginAtRisk() {
        return lossToLiquidation()
                .flatMap(loss -> margin.map(loss::percentOf))
                .map(risk -> Percentage.of(
                        risk.value().setScale(MARGIN_RISK_SCALE, RoundingMode.HALF_UP)));
    }
}
