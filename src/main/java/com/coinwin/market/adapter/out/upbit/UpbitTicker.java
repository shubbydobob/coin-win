package com.coinwin.market.adapter.out.upbit;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/**
 * 업비트 {@code /v1/ticker} 응답 한 줄에서 우리가 쓰는 것만.
 *
 * <p>키 이름을 손으로 적는다. 업비트는 {@code snake_case} 로 내는데 이 앱의 다른 응답(바이낸스)
 * 은 {@code camelCase} 라, 전역 이름 규칙을 바꾸면 <b>다른 어댑터들이 조용히 깨진다.</b>
 *
 * @param tradePrice 마지막 체결가 (원)
 * @param timestamp 체결 시각. 밀리초 단위의 epoch 다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UpbitTicker(
        @JsonProperty("trade_price") BigDecimal tradePrice,
        @JsonProperty("timestamp") Long timestamp) {
}
