package com.coinwin.account.adapter.out.binance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** {@code /fapi/v1/time} 응답. 공개 엔드포인트라 서명이 필요 없다. */
@JsonIgnoreProperties(ignoreUnknown = true)
record BinanceServerTime(@JsonProperty("serverTime") Long serverTime) {
}
