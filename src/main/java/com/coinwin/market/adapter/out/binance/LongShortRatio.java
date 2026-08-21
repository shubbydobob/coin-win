package com.coinwin.market.adapter.out.binance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * {@code /futures/data/globalLongShortAccountRatio} 응답의 한 점.
 *
 * <p>계정 수 기준 비율이지 금액 기준이 아니다. 1.83 은 롱을 든 계정이 숏을 든 계정보다
 * 1.83배 많다는 뜻이고, 그 계정들이 더 큰 포지션을 들고 있다는 뜻은 아니다.
 *
 * @param longShortRatio 롱 계정 수 / 숏 계정 수
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record LongShortRatio(String longShortRatio) {
}
