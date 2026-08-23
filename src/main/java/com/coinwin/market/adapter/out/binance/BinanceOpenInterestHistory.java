package com.coinwin.market.adapter.out.binance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** {@code /futures/data/openInterestHist} 한 건. */
@JsonIgnoreProperties(ignoreUnknown = true)
record BinanceOpenInterestHistory(String sumOpenInterest, Long timestamp) {
}
