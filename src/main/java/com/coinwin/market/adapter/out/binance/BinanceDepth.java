package com.coinwin.market.adapter.out.binance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * {@code /fapi/v1/depth} 응답.
 *
 * <p>호가 한 단이 <b>문자열 두 개짜리 배열</b>로 온다 — {@code ["76567.40", "24.778"]}. 첫째가
 * 가격, 둘째가 잔량이다. 객체가 아니라 배열이라 필드 이름이 없고, 그래서 순서를 여기서 한 번만
 * 해석해 둔다. 어댑터가 {@code get(0)} 을 직접 쓰면 그 순서가 코드 여러 곳에 흩어진다.
 *
 * @param bids 매수. 거래소는 내림차순으로 주지만 {@code OrderBook} 이 다시 정렬한다
 * @param asks 매도
 * @param eventTime 거래소 시각(epoch ms). 응답에서는 {@code E} 라는 한 글자 이름으로 온다
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record BinanceDepth(
        List<List<String>> bids,
        List<List<String>> asks,
        @JsonProperty("E") Long eventTime) {

    private static final int PRICE = 0;

    private static final int QUANTITY = 1;

    static String priceOf(List<String> level) {
        return level.get(PRICE);
    }

    static String quantityOf(List<String> level) {
        return level.get(QUANTITY);
    }
}
