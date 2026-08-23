package com.coinwin.account.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Percentage;
import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import com.coinwin.market.domain.Symbol;
import com.coinwin.position.domain.Direction;
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
        Instant observedAt) {

    public ExchangePosition {
        DomainValues.required(symbol, "종목");
        DomainValues.required(direction, "방향");
        DomainValues.required(quantity, "보유 수량");
        DomainValues.required(entryPrice, "평단");
        DomainValues.required(markPrice, "표시가");
        DomainValues.required(liquidationPrice, "청산가");
        if (liquidationPrice.isPresent() && liquidationPrice.get().value().signum() <= 0) {
            throw new InvalidAccountDataException(
                    "청산가가 있다면 0 보다 커야 한다 — 없는 것과 0 은 다른 사실이다");
        }
        DomainValues.required(unrealizedPnl, "미실현 손익");
        DomainValues.required(observedAt, "관측 시각");
        if (quantity.value().signum() <= 0) {
            throw new InvalidAccountDataException(
                    "포지션 수량은 0 보다 커야 한다: " + quantity.value().toPlainString());
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
}
