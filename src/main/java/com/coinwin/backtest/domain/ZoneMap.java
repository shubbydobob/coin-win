package com.coinwin.backtest.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.InvalidValueException;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Price;
import com.coinwin.indicator.domain.PriceBand;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * 어느 시점에 유효한 대 전체. 하단 오름차순으로 정렬돼 있다.
 *
 * <p>시점마다 다시 만든다. 대는 "지금 이 변동성에서 같은 자리로 보이는가" 이고 그 판정은
 * 지금 하기 때문이다 — 허용치가 그 시점의 ATR 에 매달려 있으므로 어제 만든 대를 오늘 쓰면
 * 어제의 변동성으로 오늘을 본다.
 *
 * <p><b>입력은 그 시점에 확정된 피벗만</b>이어야 한다({@link Pivot#isKnownAt}). 이 타입은
 * 그것을 검사하지 않는다 — 확정 여부는 시각의 문제이고 여기는 가격만 다룬다. 거르는 책임은
 * 부르는 쪽에 있고, 그 책임이 지켜지는지는 룩어헤드 접미사 불변 테스트가 확인한다.
 */
public record ZoneMap(List<PriceZone> zones) {

    public ZoneMap {
        DomainValues.required(zones, "대 목록");
        zones = List.copyOf(zones);
    }

    /**
     * 피벗을 가격 근접도로 묶어 대를 만든다.
     *
     * <p><b>가격순 단일 패스로 사슬을 잇는다.</b> 직전 피벗과의 간격이 허용치 이내면 같은
     * 군집이고, 아니면 새 군집을 연다. 중심에서의 거리로 자르면 어느 피벗을 중심으로 삼느냐에
     * 따라 결과가 달라지고 그 선택에 정답이 없다. 사슬은 입력에만 의존하므로 결정론적이다.
     *
     * <p>고점과 저점을 <b>섞어서</b> 묶는다. 같은 가격대에서 위로 막혔다가 아래로 받쳐진 자리는
     * 하나의 대다. 종류별로 나누면 같은 자리가 두 대로 세어져 터치 기준이 무력해진다.
     *
     * @param pivots 그 시점에 확정된 피벗. 순서는 결과에 영향을 주지 않는다
     * @param tolerance 같은 대로 볼 최대 간격. 보통 {@code ATR × clusterMultiple}
     * @param minTouches 대로 채택할 최소 피벗 수. 하나는 선이지 대가 아니므로 2 이상
     */
    public static ZoneMap from(List<Pivot> pivots, Money tolerance, int minTouches) {
        DomainValues.required(pivots, "피벗 목록");
        DomainValues.required(tolerance, "군집 허용치");
        assertSettings(tolerance, minTouches);
        return new ZoneMap(cluster(sortedByPrice(pivots), tolerance).stream()
                .filter(cluster -> cluster.size() >= minTouches)
                .map(ZoneMap::toZone)
                .toList());
    }

    /** 종가 아래에서 가장 가까운 대. 롱 진입 후보다. */
    public Optional<PriceZone> nearestSupport(Price close) {
        DomainValues.required(close, "종가");
        return zonesActingAs(ZoneRole.SUPPORT, close).reduce((lower, higher) -> higher);
    }

    /** 종가 위에서 가장 가까운 대. 숏 진입 후보이자 롱의 익절 목표다. */
    public Optional<PriceZone> nearestResistance(Price close) {
        DomainValues.required(close, "종가");
        return zonesActingAs(ZoneRole.RESISTANCE, close).findFirst();
    }

    /** 가격이 어느 대 안에(경계 포함) 들어 있는가. 그런 대로는 진입하지 않는다. */
    public boolean containsPrice(Price price) {
        DomainValues.required(price, "가격");
        return zones.stream().anyMatch(zone -> zone.roleAt(price).isEmpty());
    }

    private Stream<PriceZone> zonesActingAs(ZoneRole role, Price close) {
        return zones.stream().filter(zone -> zone.roleAt(close).filter(role::equals).isPresent());
    }

    private static void assertSettings(Money tolerance, int minTouches) {
        if (tolerance.isNegative()) {
            throw new InvalidValueException(
                    "군집 허용치는 음수일 수 없다: " + tolerance.value().toPlainString());
        }
        DomainValues.atLeast(minTouches, 2, "대의 터치 횟수");
    }

    /** 가격 오름차순. 같은 가격이면 발생 시각으로 갈라 입력 순서에 의존하지 않게 한다. */
    private static List<Pivot> sortedByPrice(List<Pivot> pivots) {
        return pivots.stream()
                .sorted(Comparator.comparing((Pivot pivot) -> pivot.price().value())
                        .thenComparing(Pivot::at)
                        .thenComparing(Pivot::kind))
                .toList();
    }

    private static List<List<Pivot>> cluster(List<Pivot> sorted, Money tolerance) {
        List<List<Pivot>> clusters = new ArrayList<>();
        for (Pivot pivot : sorted) {
            if (clusters.isEmpty() || isBeyond(clusters.getLast().getLast(), pivot, tolerance)) {
                clusters.add(new ArrayList<>());
            }
            clusters.getLast().add(pivot);
        }
        return clusters;
    }

    private static boolean isBeyond(Pivot previous, Pivot current, Money tolerance) {
        return previous.price().absoluteDifference(current.price()).isGreaterThan(tolerance);
    }

    private static PriceZone toZone(List<Pivot> cluster) {
        return new PriceZone(
                PriceBand.enclosing(cluster.getFirst().price(), cluster.getLast().price()),
                cluster.size());
    }
}
