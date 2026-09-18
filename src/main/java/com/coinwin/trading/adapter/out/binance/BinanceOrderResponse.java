package com.coinwin.trading.adapter.out.binance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.Optional;

/**
 * 주문을 냈을 때 바이낸스가 돌려주는 것.
 *
 * <p>수는 <b>전부 문자열</b>이다. 부동소수로 한 번 거치면 0.1 이 0.09999999 가 된다 —
 * 이 프로젝트가 값 객체를 두는 이유 그 자체다.
 *
 * @param orderId 거래소가 정한 식별자. <b>취소할 때 이것으로 가리킨다</b>
 * @param status {@code NEW} 는 걸렸다는 뜻이고 {@code FILLED} 는 체결됐다는 뜻이다
 * @param avgPrice 평균 체결가. 걸리기만 한 주문은 {@code "0"} 이다
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record BinanceOrderResponse(
        @JsonProperty("orderId") Long orderId,
        @JsonProperty("status") String status,
        @JsonProperty("avgPrice") String avgPrice) {

    /**
     * 체결가. <b>거래소는 아직 안 채워진 주문에 {@code "0"} 을 준다</b> — 그것을 담으면
     * "0 원에 체결됐다" 가 되고 그 수가 손익 계산에 그대로 들어간다. 청산가와 같은 규칙이다.
     */
    Optional<BigDecimal> filledAt() {
        if (avgPrice == null) {
            return Optional.empty();
        }
        BigDecimal value = new BigDecimal(avgPrice);
        return value.signum() > 0 ? Optional.of(value) : Optional.empty();
    }
}
