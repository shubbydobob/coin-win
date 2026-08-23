package com.coinwin.market.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.InvalidValueException;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Percentage;
import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * 한 순간의 호가. 매수는 높은 값부터, 매도는 낮은 값부터다.
 *
 * <p><b>이 타입은 방향을 말하지 않는다.</b> 불균형이 양수라고 오른다는 뜻이 아니다 — 호가는
 * 취소될 수 있고 큰 벽은 오히려 미끼인 경우가 많다. 답하는 것은 "지금 유동성이 어느 쪽에
 * 얇은가" 하나이며, 얇은 쪽으로 가격이 빨리 움직이는 것은 예측이 아니라 체결의 성질이다.
 *
 * <p><b>관측 시각을 함께 담는다.</b> 이 값은 다음 순간이면 달라지고, 언제 본 것인지 없이
 * 화면에 놓으면 사람이 그것을 현재로 읽는다. {@code ExchangePosition} 과 같은 이유다.
 *
 * <p>근거: {@code docs/spec/market-watch-design.md} § 2
 */
public record OrderBook(Symbol symbol, List<PriceLevel> bids, List<PriceLevel> asks, Instant at) {

    /** 불균형의 자릿수. 손익비·롱숏비율과 같은 무차원 비(比)다. */
    private static final int RATIO_SCALE = 4;

    public OrderBook {
        DomainValues.required(symbol, "종목");
        DomainValues.required(at, "관측 시각");
        bids = List.copyOf(sorted(bids, "매수 호가", Comparator.reverseOrder()));
        asks = List.copyOf(sorted(asks, "매도 호가", Comparator.naturalOrder()));
        if (asks.getFirst().price().isBelow(bids.getFirst().price())) {
            throw new InvalidValueException("최우선 매도가는 최우선 매수가보다 낮을 수 없다");
        }
    }

    public Price bestBid() {
        return bids.getFirst().price();
    }

    public Price bestAsk() {
        return asks.getFirst().price();
    }

    /** 최우선 매수와 매도의 간격. 가격이 아니라 <b>가격 거리</b>이므로 {@link Money} 다. */
    public Money spread() {
        return bestAsk().absoluteDifference(bestBid());
    }

    /** 스프레드가 최우선 매도가의 몇 %인가. 종목이 달라도 비교할 수 있게 하는 것이 목적이다. */
    public Percentage spreadPercent() {
        return Percentage.of(ratio(spread().value(), bestAsk().value())
                .multiply(BigDecimal.valueOf(100)));
    }

    public Quantity bidVolume() {
        return volume(bids);
    }

    public Quantity askVolume() {
        return volume(asks);
    }

    /**
     * {@code (매수 잔량 - 매도 잔량) / (매수 잔량 + 매도 잔량)}. 양수면 매수가 두껍다.
     *
     * <p>합으로 나누므로 언제나 -1 과 1 사이이고, 그래서 <b>거래량이 많은 날과 적은 날을
     * 나란히 놓을 수 있다.</b> 차이만 쓰면 그 비교가 성립하지 않는다.
     */
    public BigDecimal imbalance() {
        BigDecimal bid = bidVolume().value();
        BigDecimal ask = askVolume().value();
        return ratio(bid.subtract(ask), bid.add(ask));
    }

    private static Quantity volume(List<PriceLevel> levels) {
        return levels.stream()
                .map(PriceLevel::quantity)
                .reduce(Quantity::plus)
                .orElseThrow();
    }

    private static BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
        return numerator.divide(denominator, RATIO_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 거래소가 준 순서를 믿지 않고 다시 세운다. 최우선 호가는 순서가 정의하는데, 그 순서가
     * 어댑터의 성실함에 달려 있으면 매핑이 한 번 어긋날 때 스프레드가 음수로 나온다.
     *
     * <p>불변 복사는 부르는 쪽(생성자)에서 한다. SpotBugs 의 흐름 분석이 헬퍼 안까지 따라오지
     * 못해, 여기서 감싸면 {@code EI_EXPOSE_REP} 가 그대로 남는다. {@code CandleSeries} 도
     * 생성자에서 직접 {@code List.copyOf} 를 부른다.
     */
    private static List<PriceLevel> sorted(
            List<PriceLevel> levels, String label, Comparator<BigDecimal> order) {
        DomainValues.required(levels, label);
        if (levels.isEmpty()) {
            throw new InvalidValueException(label + "은(는) 최소 1건이어야 한다");
        }
        return levels.stream()
                .sorted(Comparator.comparing(level -> level.price().value(), order))
                .toList();
    }
}
