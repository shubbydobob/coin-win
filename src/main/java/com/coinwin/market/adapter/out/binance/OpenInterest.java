package com.coinwin.market.adapter.out.binance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * {@code /fapi/v1/openInterest} 응답.
 *
 * @param openInterest 미결제약정. BTCUSDT 무기한은 기초자산(BTC) 수량으로 온다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record OpenInterest(String openInterest) {
}
