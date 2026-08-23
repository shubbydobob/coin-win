package com.coinwin.market.adapter.out.binance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * {@code /futures/data/takerlongshortRatio} 한 건.
 *
 * @param buySellRatio 시장가 매수량 ÷ 시장가 매도량. <b>계정 수가 아니라 체결량</b>이다
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record BinanceTakerRatio(String buySellRatio, String buyVol, String sellVol, Long timestamp) {
}
