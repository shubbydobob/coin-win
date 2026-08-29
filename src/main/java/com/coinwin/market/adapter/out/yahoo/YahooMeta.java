package com.coinwin.market.adapter.out.yahoo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

/**
 * 야후 응답의 {@code meta} 에서 두 칸.
 *
 * <p>{@code chartPreviousClose} 를 쓰는 이유는 {@code previousClose} 가 없는 티커가 있어서다.
 * 지수와 선물에서는 전자가 언제나 온다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record YahooMeta(BigDecimal regularMarketPrice, BigDecimal chartPreviousClose) {
}
