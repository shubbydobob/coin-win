package com.coinwin.market.adapter.out.binance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * {@code /fapi/v1/premiumIndex} 응답에서 우리가 쓰는 두 자리.
 *
 * <p>{@code ignoreUnknown} 을 켠 이유는 거래소가 필드를 <b>추가</b>하는 일이 정상이기
 * 때문이다. 새 필드 하나에 어댑터가 깨지면 우리 잘못이다. 반대로 우리가 읽는 필드가
 * 사라지면 그때는 깨지는 것이 맞고, 실제로 깨진다.
 *
 * @param lastFundingRate 비율 표기의 펀딩비. {@code "0.00010000"} 은 0.01% 다.
 * @param time 이 값의 관측 시각 (epoch millis)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record PremiumIndex(String lastFundingRate, long time) {
}
