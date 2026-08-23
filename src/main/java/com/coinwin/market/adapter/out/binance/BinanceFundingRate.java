package com.coinwin.market.adapter.out.binance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * {@code /fapi/v1/fundingRate} 한 건.
 *
 * @param fundingRate <b>비율</b>이다({@code "0.00010000"} 이 0.01%). 표본을 만들 때 퍼센트로
 *     옮기는 것은 {@code FundingRate} 가 이미 아는 규칙이다
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record BinanceFundingRate(String fundingRate, Long fundingTime) {
}
