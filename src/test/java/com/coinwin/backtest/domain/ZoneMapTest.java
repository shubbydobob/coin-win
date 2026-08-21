package com.coinwin.backtest.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.backtest.BacktestFixtures;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Price;
import com.coinwin.position.domain.Direction;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ZoneMapTest {

    private static final Money TOLERANCE = Money.of("10");
    private static final int MIN_TOUCHES = 2;

    private static Pivot high(int index, String price) {
        return new Pivot(BacktestFixtures.hour(index), BacktestFixtures.hour(index + 2),
                Price.of(price), PivotKind.SWING_HIGH);
    }

    private static Pivot low(int index, String price) {
        return new Pivot(BacktestFixtures.hour(index), BacktestFixtures.hour(index + 2),
                Price.of(price), PivotKind.SWING_LOW);
    }

    private static ZoneMap mapOf(Pivot... pivots) {
        return ZoneMap.from(List.of(pivots), TOLERANCE, MIN_TOUCHES);
    }

    @Test
    void 허용치_안의_피벗들은_한_대로_묶이고_폭은_실제_최소_최대다() {
        ZoneMap map = mapOf(high(0, "100"), high(5, "108"), high(10, "104"));

        assertThat(map.zones()).singleElement().satisfies(zone -> {
            assertThat(zone.band().lower()).isEqualTo(Price.of("100"));
            assertThat(zone.band().upper()).isEqualTo(Price.of("108"));
            assertThat(zone.touches()).isEqualTo(3);
            assertThat(zone.width()).isEqualTo(Money.of("8"));
        });
    }

    @Test
    void 허용치를_넘게_떨어진_피벗은_다른_대다() {
        ZoneMap map = mapOf(high(0, "100"), high(5, "105"), high(10, "200"), high(15, "203"));

        assertThat(map.zones()).extracting(zone -> zone.band().lower())
                .containsExactly(Price.of("100"), Price.of("200"));
    }

    /**
     * 군집은 <b>직전 피벗과의 간격</b>으로 이어 붙인다. 그래서 100·109·118 은 양 끝이 18 만큼
     * 떨어져 있어도 한 대다.
     *
     * <p>중심에서의 거리로 자르면 어느 피벗을 중심으로 삼느냐에 따라 결과가 달라지고,
     * 그 선택에 정답이 없다. 사슬로 이으면 입력에만 의존한다.
     */
    @Test
    void 군집은_사슬로_이어지므로_양_끝은_허용치보다_멀_수_있다() {
        ZoneMap map = mapOf(high(0, "100"), high(5, "109"), high(10, "118"));

        assertThat(map.zones()).singleElement().satisfies(zone -> {
            assertThat(zone.touches()).isEqualTo(3);
            assertThat(zone.width()).isEqualTo(Money.of("18"));
        });
    }

    /** 피벗 하나는 선이지 대가 아니다. 터치 횟수가 곧 강도라는 것이 이 정의의 요점이다. */
    @Test
    void 터치가_최소_기준에_못_미치는_군집은_버린다() {
        ZoneMap map = mapOf(high(0, "100"), high(5, "300"), high(10, "305"));

        assertThat(map.zones()).singleElement()
                .satisfies(zone -> assertThat(zone.band().lower()).isEqualTo(Price.of("300")));
    }

    /**
     * 고점과 저점을 섞어서 묶는다. 같은 가격대에서 위로 막혔다가 아래로 받쳐진 자리는
     * 하나의 대다. 종류별로 나누면 같은 자리가 두 대로 세어져 터치 기준이 무력해진다.
     */
    @Test
    void 고점과_저점은_같은_대로_묶인다() {
        ZoneMap map = mapOf(high(0, "100"), low(5, "103"));

        assertThat(map.zones()).singleElement()
                .satisfies(zone -> assertThat(zone.touches()).isEqualTo(2));
    }

    /**
     * 결정론의 토대. 같은 피벗 집합이면 순서가 어떻든 같은 대가 나와야 한다.
     *
     * <p>이것이 깨지면 완료 조건("동일 파라미터 재실행 시 결과 완전 동일")이 대 단계에서부터
     * 무너진다.
     */
    @Test
    void 군집_결과는_피벗_입력_순서에_의존하지_않는다() {
        List<Pivot> pivots = new ArrayList<>(List.of(
                high(0, "100"), low(1, "104"), high(2, "300"), low(3, "306"), high(4, "108")));
        ZoneMap forward = ZoneMap.from(pivots, TOLERANCE, MIN_TOUCHES);

        List<Pivot> reversed = new ArrayList<>(pivots);
        java.util.Collections.reverse(reversed);

        assertThat(ZoneMap.from(reversed, TOLERANCE, MIN_TOUCHES)).isEqualTo(forward);
    }

    @Test
    void 대는_하단_오름차순으로_정렬된다() {
        ZoneMap map = mapOf(high(0, "300"), high(1, "305"), high(2, "100"), high(3, "104"));

        assertThat(map.zones()).extracting(zone -> zone.band().lower())
                .containsExactly(Price.of("100"), Price.of("300"));
    }

    // ── 역할 — 저장하지 않고 현재 종가로 파생한다 ──────────────────────────────

    @Test
    void 종가가_대_위에_있으면_지지_아래에_있으면_저항이다() {
        PriceZone zone = mapOf(high(0, "100"), high(5, "108")).zones().getFirst();

        assertThat(zone.roleAt(Price.of("120"))).contains(ZoneRole.SUPPORT);
        assertThat(zone.roleAt(Price.of("50"))).contains(ZoneRole.RESISTANCE);
    }

    /**
     * 한 번 뚫린 대는 역할이 바뀐다. 돌파를 감지해 플래그를 뒤집는 것이 아니라 <b>같은 대에
     * 같은 질문을 다시 하는 것</b>이므로, "언제 뚫린 것으로 보는가" 라는 두 번째 정의가 필요 없다.
     */
    @Test
    void 가격이_대를_넘어가면_같은_대의_역할이_뒤집힌다() {
        PriceZone zone = mapOf(high(0, "100"), high(5, "108")).zones().getFirst();

        assertThat(zone.roleAt(Price.of("90"))).contains(ZoneRole.RESISTANCE);
        assertThat(zone.roleAt(Price.of("130"))).contains(ZoneRole.SUPPORT);
    }

    /** 경계는 구간에 포함된다({@code BandPosition.INSIDE}). 대 안에서는 근단이 정해지지 않는다. */
    @Test
    void 대_안이나_경계_위에서는_역할이_없다() {
        PriceZone zone = mapOf(high(0, "100"), high(5, "108")).zones().getFirst();

        assertThat(zone.roleAt(Price.of("104"))).isEmpty();
        assertThat(zone.roleAt(Price.of("100"))).isEmpty();
        assertThat(zone.roleAt(Price.of("108"))).isEmpty();
    }

    @Test
    void 지지는_롱_저항은_숏으로_진입한다() {
        assertThat(ZoneRole.SUPPORT.entryDirection()).isEqualTo(Direction.LONG);
        assertThat(ZoneRole.RESISTANCE.entryDirection()).isEqualTo(Direction.SHORT);
    }

    /** 롱은 위에서 내려와 상단에 먼저 닿고, 숏은 아래에서 올라와 하단에 먼저 닿는다. */
    @Test
    void 근단과_원단은_진입_방향이_정한다() {
        PriceZone zone = mapOf(high(0, "100"), high(5, "108")).zones().getFirst();

        assertThat(zone.nearEdgeFor(Direction.LONG)).isEqualTo(Price.of("108"));
        assertThat(zone.farEdgeFor(Direction.LONG)).isEqualTo(Price.of("100"));
        assertThat(zone.nearEdgeFor(Direction.SHORT)).isEqualTo(Price.of("100"));
        assertThat(zone.farEdgeFor(Direction.SHORT)).isEqualTo(Price.of("108"));
    }

    // ── 조회 ──────────────────────────────────────────────────────────────

    @Test
    void 최근접_지지는_종가_아래에서_가장_가까운_대다() {
        ZoneMap map = mapOf(high(0, "100"), high(1, "104"), high(2, "200"), high(3, "204"),
                high(4, "500"), high(5, "504"));

        assertThat(map.nearestSupport(Price.of("300")))
                .hasValueSatisfying(zone -> assertThat(zone.band().upper())
                        .isEqualTo(Price.of("204")));
        assertThat(map.nearestResistance(Price.of("300")))
                .hasValueSatisfying(zone -> assertThat(zone.band().lower())
                        .isEqualTo(Price.of("500")));
    }

    @Test
    void 반대편에_대가_없으면_비어_있다() {
        ZoneMap map = mapOf(high(0, "100"), high(1, "104"));

        assertThat(map.nearestSupport(Price.of("50"))).isEmpty();
        assertThat(map.nearestResistance(Price.of("300"))).isEmpty();
    }

    /** 가격이 대 안에 있으면 그 대는 지지도 저항도 아니므로 양쪽 조회에서 모두 빠진다. */
    @Test
    void 가격을_품고_있는_대는_지지로도_저항으로도_잡히지_않는다() {
        ZoneMap map = mapOf(high(0, "100"), high(1, "108"));

        assertThat(map.nearestSupport(Price.of("104"))).isEmpty();
        assertThat(map.nearestResistance(Price.of("104"))).isEmpty();
        assertThat(map.containsPrice(Price.of("104"))).isTrue();
        assertThat(map.containsPrice(Price.of("300"))).isFalse();
    }

    @Test
    void 피벗이_없으면_대도_없다() {
        assertThat(ZoneMap.from(List.of(), TOLERANCE, MIN_TOUCHES).zones()).isEmpty();
    }

    @Test
    void 최소_터치는_2_이상이어야_한다() {
        assertThatThrownBy(() -> ZoneMap.from(List.of(), TOLERANCE, 1))
                .isInstanceOf(InvalidBacktestException.class)
                .hasMessageContaining("터치");
    }

    @Test
    void 허용치는_음수일_수_없다() {
        assertThatThrownBy(() -> ZoneMap.from(List.of(), Money.of("-1"), MIN_TOUCHES))
                .isInstanceOf(InvalidBacktestException.class);
    }
}
