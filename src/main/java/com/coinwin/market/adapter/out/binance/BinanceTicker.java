package com.coinwin.market.adapter.out.binance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * {@code /fapi/v1/ticker/24hr} 응답.
 *
 * @param priceChangePercent <b>이미 퍼센트</b>다({@code "-1.009"}). 100 을 곱하지 않는다
 * @param closeTime 이 값들이 집계된 시각(epoch ms)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record BinanceTicker(
        String lastPrice,
        String priceChangePercent,
        String highPrice,
        String lowPrice,
        String volume,
        Long closeTime) {
}
