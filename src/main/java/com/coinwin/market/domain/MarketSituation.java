package com.coinwin.market.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 두 수가 함께 움직였을 때 <b>기계적으로 성립하는 사실</b>.
 *
 * <p>{@code 미결제약정 −3.2% / 가격 +1.1%} 를 보고 "포지션이 청산으로 줄면서 가격이 올랐다"
 * 를 읽어 내는 것은 해석이 아니라 산술이다. 그것을 사람이 매번 하지 않게 하는 것이 이 타입의
 * 전부다.
 *
 * <p><b>규칙 셋을 코드가 지킨다</b>({@code docs/spec/market-watch.md} § 6.5.3).
 *
 * <ol>
 *   <li><b>일어난 일만 적는다.</b> "포지션이 청산으로 줄면서 가격이 올랐다" 는 되고,
 *       "따라 붙어라" 는 안 된다. 문장에 동사의 주어는 언제나 <b>시장</b>이지 사용자가 아니다.
 *   <li><b>방향을 말하지 않는다.</b> 오를지 내릴지는 이 프로젝트가 답하지 않는다
 *       ({@code scope.md}).
 *   <li><b>조건이 여기 상수로 있다.</b> 조건이 안 맞으면 아무 문장도 뜨지 않고,
 *       <b>뜨지 않는 것이 기본값이다.</b>
 * </ol>
 *
 * <p>세 번째가 가장 중요하다. 문장이 늘 떠 있으면 그것은 배경이 되고, 배경이 된 경고는
 * 아무것도 경고하지 않는다 — {@code PositionReconciliation} 의 {@code ADVICE} 가 "불일치"
 * 만 띄우지 않기로 한 것과 같은 판단이다.
 */
public record MarketSituation(Kind kind, String description) {

    /** 미결제약정이 이만큼 넘게 줄면 "줄었다" 로 본다. 30분 기준 −1%. */
    private static final BigDecimal OPEN_INTEREST_DROP = new BigDecimal("-0.01");

    /** 미결제약정이 이만큼 넘게 늘면 "쌓였다" 로 본다. */
    private static final BigDecimal OPEN_INTEREST_RISE = new BigDecimal("0.01");

    /** 가격이 이만큼 넘게 움직이면 "움직였다" 로 본다. 30분 기준 ±0.5%. */
    private static final BigDecimal PRICE_MOVE = new BigDecimal("0.005");

    /** 펀딩비가 이만큼을 넘으면 한쪽이 뚜렷하게 비용을 내고 있는 것으로 본다. (%) */
    private static final BigDecimal FUNDING_SKEW = new BigDecimal("0.02");

    public enum Kind {
        /** 포지션이 줄면서 가격이 올랐다. 숏이 정리되는 형태다. */
        SHORT_UNWIND,

        /** 포지션이 줄면서 가격이 내렸다. 롱이 정리되는 형태다. */
        LONG_UNWIND,

        /** 포지션이 쌓이면서 가격이 올랐다. 새 돈이 위쪽에 들어온 형태다. */
        BUILDING_UP,

        /** 포지션이 쌓이면서 가격이 내렸다. 새 돈이 아래쪽에 들어온 형태다. */
        BUILDING_DOWN,

        /** 한쪽이 뚜렷하게 펀딩비를 내고 있다. */
        FUNDING_SKEWED
    }

    /**
     * 지금 성립하는 사실들. <b>비어 있는 것이 정상이다.</b>
     *
     * <p>미결제약정과 가격을 함께 보는 것이 첫 짝이다. 포지션이 <b>줄면서</b> 가격이 움직였다면
     * 그 움직임은 새 돈이 아니라 <b>청산·정리</b>가 만든 것이다 — 계기가 된 사건이 정확히
     * 이 모양이었다.
     */
    public static List<MarketSituation> of(List<MetricOutlier> metrics, MetricOutlier price) {
        List<MarketSituation> found = new ArrayList<>();
        positionAndPrice(metrics, price).ifPresent(found::add);
        fundingSkew(metrics).ifPresent(found::add);
        return List.copyOf(found);
    }

    private static Optional<MarketSituation> positionAndPrice(
            List<MetricOutlier> metrics, MetricOutlier price) {
        Optional<BigDecimal> openInterest = changeOf(metrics, MetricKind.OPEN_INTEREST);
        Optional<BigDecimal> moved = price == null ? Optional.empty() : price.change();
        if (openInterest.isEmpty() || moved.isEmpty()) {
            return Optional.empty();
        }
        return describe(openInterest.get(), moved.get());
    }

    /**
     * 두 수의 부호 조합이 곧 답이다.
     *
     * <p>조건 넷을 {@code if} 로 늘어놓으면 복잡도가 한계를 넘는데, 그 한계에 걸린 것이
     * <b>신호였다</b> — 실제로 여기 있는 것은 분기가 아니라 <b>2×2 표</b>다. 포지션이
     * 늘었나 줄었나, 가격이 올랐나 내렸나.
     */
    private static Optional<MarketSituation> describe(BigDecimal openInterest, BigDecimal moved) {
        if (moved.abs().compareTo(PRICE_MOVE) < 0) {
            return Optional.empty();
        }
        boolean up = moved.signum() > 0;
        if (openInterest.compareTo(OPEN_INTEREST_DROP) < 0) {
            return Optional.of(up ? unwind(Kind.SHORT_UNWIND, "올랐다") : unwind(Kind.LONG_UNWIND, "내렸다"));
        }
        if (openInterest.compareTo(OPEN_INTEREST_RISE) > 0) {
            return Optional.of(up ? building(Kind.BUILDING_UP, "올랐다") : building(Kind.BUILDING_DOWN, "내렸다"));
        }
        return Optional.empty();
    }

    private static MarketSituation unwind(Kind kind, String moved) {
        return new MarketSituation(
                kind,
                "미결제약정이 줄면서 가격이 %s — 새로 들어온 돈이 아니라 포지션이 정리되며 만들어진 움직임이다"
                        .formatted(moved));
    }

    private static MarketSituation building(Kind kind, String moved) {
        return new MarketSituation(
                kind, "미결제약정이 늘면서 가격이 %s — 새 포지션이 들어오고 있다".formatted(moved));
    }

    private static Optional<MarketSituation> fundingSkew(List<MetricOutlier> metrics) {
        return metrics.stream()
                .filter(metric -> metric.kind() == MetricKind.FUNDING_RATE)
                .findFirst()
                .filter(metric -> metric.current().abs().compareTo(FUNDING_SKEW) > 0)
                .map(metric -> new MarketSituation(
                        Kind.FUNDING_SKEWED,
                        metric.current().signum() > 0
                                ? "롱이 숏에게 펀딩비를 내고 있다 — 들고 있는 데 비용이 든다"
                                : "숏이 롱에게 펀딩비를 내고 있다 — 들고 있는 데 비용이 든다"));
    }

    private static Optional<BigDecimal> changeOf(List<MetricOutlier> metrics, MetricKind kind) {
        return metrics.stream()
                .filter(metric -> metric.kind() == kind)
                .findFirst()
                .flatMap(MetricOutlier::change);
    }
}
