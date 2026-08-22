package com.coinwin.account.adapter.out.binance;

import com.coinwin.account.domain.ExchangePosition;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import com.coinwin.market.domain.Symbol;
import com.coinwin.position.domain.Direction;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/**
 * {@code /fapi/v3/positionRisk} 응답 한 줄.
 *
 * <p>거래소가 주는 것은 <b>전부 문자열</b>이다. 그대로 두는 이유는 부동소수로 한 번 거치면
 * 0.1 이 0.09999999 가 되기 때문이다 — 이 프로젝트가 값 객체를 두는 이유 그 자체이고, 수량
 * 비교에 허용 오차를 두지 않기로 한 결정이 여기에 걸려 있다.
 *
 * <p><b>필드 이름을 명시한다.</b> 레코드 파라미터 이름에 기대면 컴파일 플래그({@code
 * -parameters}) 하나로 매핑이 조용히 깨진다. 응답이 {@code null} 로 채워져도 예외가 나지
 * 않으므로 그 고장은 "포지션이 없다" 로 보인다.
 *
 * <p>모르는 필드는 무시한다. 거래소가 필드를 더하는 것은 우리 고장이 아니다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record BinancePositionRisk(
        @JsonProperty("symbol") String symbol,
        @JsonProperty("positionAmt") String positionAmt,
        @JsonProperty("entryPrice") String entryPrice,
        @JsonProperty("liquidationPrice") String liquidationPrice,
        @JsonProperty("unRealizedProfit") String unrealizedProfit) {

    /**
     * 열려 있는 포지션인가.
     *
     * <p>거래소는 한 번이라도 연 적 있는 종목을 전부 돌려주고 닫힌 것은 {@code "0"} 이다.
     * 부호는 방향이므로 <b>0 과 다른지</b>만 본다.
     */
    boolean isOpen() {
        return positionAmt != null && new BigDecimal(positionAmt).signum() != 0;
    }

    ExchangePosition toDomain(Instant observedAt) {
        BigDecimal amount = new BigDecimal(positionAmt);
        return new ExchangePosition(
                new Symbol(symbol),
                amount.signum() > 0 ? Direction.LONG : Direction.SHORT,
                Quantity.of(amount.abs().toPlainString()),
                Price.of(entryPrice),
                liquidationPriceOrNone(),
                Money.of(unrealizedProfit),
                observedAt);
    }

    /**
     * 청산가. <b>거래소가 말할 수 없을 때 {@code "0"} 을 준다</b> — 격리 마진이 아니거나
     * 잔고가 충분해 청산 지점이 정의되지 않는 경우다. 0 원짜리 청산가로 담으면 화면이
     * "곧 청산된다" 는 뜻으로 읽는다.
     */
    private Optional<Price> liquidationPriceOrNone() {
        if (liquidationPrice == null || new BigDecimal(liquidationPrice).signum() <= 0) {
            return Optional.empty();
        }
        return Optional.of(Price.of(liquidationPrice));
    }
}
