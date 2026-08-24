package com.coinwin.market.adapter.out.binance;

import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import com.coinwin.market.domain.OrderBook;
import com.coinwin.market.domain.PriceLevel;
import com.coinwin.market.domain.Symbol;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

/**
 * 한 뭉치의 호가. <b>같은 사실이 두 경로로 오고 이름이 다르다.</b>
 *
 * <p>REST {@code /fapi/v1/depth} — 이름이 길다.
 *
 * <pre>{@code {"lastUpdateId":..., "bids":[["78562.70","0.775"],...], "asks":[...]} }</pre>
 *
 * <p>웹소켓 {@code btcusdt@depth20@100ms} — 한 글자씩이고 종목이 실려 온다. 아래는 실제로 받은
 * 것을 줄인 것이다(2026-08-25).
 *
 * <pre>{@code {"e":"depthUpdate","E":1787592539515,"s":"BTCUSDT",
 *  "b":[["78562.70","0.775"],...],"a":[...]} }</pre>
 *
 * <p>둘을 한 타입으로 받는 이유는 <b>같은 것이기 때문이다.</b> 따로 두면 "가격이 배열의 0번"
 * 이라는 해석과 대 만들기가 두 벌이 되고, 한쪽만 고쳐지는 날 스트림과 REST 가 다른 호가를
 * 낸다. 이름의 차이는 거래소의 사정이므로 그 흡수는 DTO 가 할 일이다.
 *
 * <p>호가 한 단이 <b>문자열 두 개짜리 배열</b>로 온다 — {@code ["78562.70","0.775"]}. 첫째가
 * 가격, 둘째가 잔량이다. 객체가 아니라 배열이라 필드 이름이 없고, 그래서 순서를 여기서 한 번만
 * 해석해 둔다.
 *
 * @param bids 매수. 거래소는 내림차순으로 주지만 {@code OrderBook} 이 다시 정렬한다
 * @param asks 매도
 * @param eventTime 거래소 시각(epoch ms). 응답에서는 {@code E} 라는 한 글자 이름으로 온다
 * @param symbol 종목. <b>스트림에만 있다</b> — REST 는 우리가 물어본 종목이라 되돌려주지 않는다
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record BinanceDepth(
        @JsonAlias("b") List<List<String>> bids,
        @JsonAlias("a") List<List<String>> asks,
        @JsonProperty("E") Long eventTime,
        @JsonProperty("s") String symbol) {

    private static final int PRICE = 0;

    private static final int QUANTITY = 1;

    /**
     * 도메인 호가로 옮긴다.
     *
     * <p>{@code observedAt} 은 <b>거래소가 시각을 주지 않았을 때만</b> 쓰인다. 관측 시각 없이
     * 호가를 만들 수는 없는데, 우리 시계로 찍은 값을 거래소 시각인 척 두면 기계가 어긋나 있는
     * 만큼 "언제 본 값인가" 가 통째로 틀린다({@code BinanceServerClock} 이 33초를 잡은 자리).
     */
    OrderBook toOrderBook(Symbol requested, Instant observedAt) {
        if (bids == null || asks == null) {
            throw new BinanceResponseException("호가가 비어 있다: " + requested.value());
        }
        return new OrderBook(requested, levels(bids), levels(asks), at(observedAt));
    }

    /** 스트림이 말한 종목. 없으면 어느 종목의 호가인지 알 수 없으므로 버린다. */
    Symbol streamedSymbol() {
        if (symbol == null) {
            throw new BinanceResponseException("스트림 호가에 종목이 없다");
        }
        return Symbol.of(symbol);
    }

    private Instant at(Instant observedAt) {
        return eventTime == null ? observedAt : Instant.ofEpochMilli(eventTime);
    }

    private static List<PriceLevel> levels(List<List<String>> raw) {
        return raw.stream()
                .map(level -> new PriceLevel(
                        Price.of(level.get(PRICE)), Quantity.of(level.get(QUANTITY))))
                .toList();
    }
}
