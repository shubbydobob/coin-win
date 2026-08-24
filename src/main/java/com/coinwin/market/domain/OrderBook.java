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
import java.util.Optional;

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

    /**
     * 한 단 평균의 몇 배부터 벽으로 볼 것인가.
     *
     * <p>3배는 <b>근거 있는 수가 아니라 자리표시자다</b> — 슬리피지 기본값이나 매물대 배수와
     * 같은 자리이며, 실제 호가에서 얼마가 드문지는 재 본 적이 없다. 그래도 상수로 박아 두는
     * 이유는 이 판단이 화면과 도메인 두 곳에 생기는 것을 막기 위해서다.
     */
    private static final BigDecimal WALL_MULTIPLE = new BigDecimal("3");

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

    /**
     * 매수 쪽에서 가장 두꺼운 한 단. <b>평소보다 두꺼울 때만</b> 낸다.
     *
     * <p>기준을 넘지 않으면 비어 있다 — 언제나 "가장 두꺼운 단" 을 내면 그것은 그냥 최댓값이고,
     * 화면에 늘 떠 있는 것은 아무것도 알려 주지 않는다. 상황 문장이 조건을 못 넘으면 아예
     * 안 뜨게 한 것과 같은 규칙이다.
     */
    public Optional<OrderWall> biggestBid() {
        return wallIn(bids);
    }

    /** 매도 쪽에서 가장 두꺼운 한 단. */
    public Optional<OrderWall> biggestAsk() {
        return wallIn(asks);
    }

    private static Optional<OrderWall> wallIn(List<PriceLevel> levels) {
        BigDecimal average = averageOf(levels);
        if (average.signum() == 0) {
            return Optional.empty();
        }
        PriceLevel thickest = levels.stream()
                .max(Comparator.comparing(level -> level.quantity().value()))
                .orElseThrow();
        BigDecimal multiple = ratio(thickest.quantity().value(), average);
        return multiple.compareTo(WALL_MULTIPLE) < 0
                ? Optional.empty()
                : Optional.of(new OrderWall(thickest, multiple));
    }

    private static BigDecimal averageOf(List<PriceLevel> levels) {
        return volume(levels).value()
                .divide(BigDecimal.valueOf(levels.size()), RATIO_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 위에서 {@code depth} 단만 남긴 호가.
     *
     * <p>부분 호가 스트림이 20단을 밀어 주는데 화면은 5단을 물을 수 있어서 생겼다. 자르는
     * 규칙을 어댑터마다 두면 인메모리와 스트림이 <b>같은 요청에 다르게 답할 수 있다</b> —
     * {@code OrderBookDepth} 를 서비스에서 도메인으로 내린 것과 같은 이유다.
     *
     * <p><b>모자라면 있는 만큼만 준다.</b> 20단을 물었는데 12단뿐인 것은 거래소가 그만큼만
     * 가진 것이지 오류가 아니다. "이 호가가 그 깊이를 담당하는가" 는 부르는 쪽이 판단한다.
     */
    public OrderBook truncatedTo(OrderBookDepth depth) {
        return new OrderBook(symbol, top(bids, depth), top(asks, depth), at);
    }

    private static List<PriceLevel> top(List<PriceLevel> levels, OrderBookDepth depth) {
        return levels.subList(0, Math.min(depth.levels(), levels.size()));
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
