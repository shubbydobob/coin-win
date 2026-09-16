package com.coinwin.account.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.account.AccountFixtures;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import com.coinwin.position.domain.Direction;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * 규칙 R1 — <b>손절 없는 포지션은 존재할 수 없다.</b>
 *
 * <p>여기서 판정하는 것은 "존재하는가" 이고 강제하는 것이 아니다. 1단계는 읽기 전용 경고이며,
 * 거는 것은 거래 권한이 필요한 3단계다({@code docs/spec/exit-automation.md} § 5).
 */
class PositionProtectionTest {

    private static final ExchangePosition LONG = AccountFixtures.longPosition("0.1");

    @Test
    void 손절이_하나도_없으면_보호되지_않는다() {
        assertThat(protection(List.of()).coverage()).isEqualTo(StopLossCoverage.NONE);
    }

    /**
     * <b>익절만 걸어 둔 것은 손절이 아니다.</b> {@code exit-automation.md} § 0 의 진술이 정확히
     * 이 모양이다 — 버는 쪽만 준비하고 잃는 쪽을 비워 둔 것이라, 아무것도 안 건 것과 다른
     * 말을 화면이 해야 한다.
     */
    @Test
    void 익절만_걸려_있으면_손절이_없는_것이다() {
        PositionProtection protection =
                protection(List.of(AccountFixtures.takeProfit(Direction.LONG, "64000")));

        assertThat(protection.coverage()).isEqualTo(StopLossCoverage.NONE);
        assertThat(protection.takeProfitWithoutStopLoss()).isTrue();
    }

    @Test
    void 손절이_있으면_익절만_있는_상태가_아니다() {
        PositionProtection protection = protection(List.of(
                AccountFixtures.takeProfit(Direction.LONG, "64000"),
                AccountFixtures.stop(Direction.LONG, "58000")));

        assertThat(protection.takeProfitWithoutStopLoss()).isFalse();
    }

    @Test
    void 추격_손절만_있어도_전량_보호다() {
        assertThat(protection(List.of(AccountFixtures.trailingStop(Direction.LONG))).coverage())
                .isEqualTo(StopLossCoverage.FULL);
    }

    /** 물타기로 수량이 늘었는데 손절은 옛 수량 그대로인 상태. −2,000 이 자라던 모양이다. */
    @Test
    void 수량이_모자라면_부분_보호다() {
        PositionProtection protection = protection(
                List.of(AccountFixtures.partialStop(Direction.LONG, "58000", "0.04")));

        assertThat(protection.coverage()).isEqualTo(StopLossCoverage.PARTIAL);
        assertThat(protection.stoppedQuantity()).isEqualTo(Quantity.of("0.04"));
    }

    @Test
    void 부분_손절_둘이_합쳐_전량이면_전량_보호다() {
        PositionProtection protection = protection(List.of(
                AccountFixtures.partialStop(Direction.LONG, "58000", "0.06"),
                AccountFixtures.partialStop(Direction.LONG, "57000", "0.04")));

        assertThat(protection.coverage()).isEqualTo(StopLossCoverage.FULL);
    }

    /** 덮인 수량은 포지션 수량을 넘지 않는다. 넘겨 세면 부분 보호가 전량으로 보인다. */
    @Test
    void 덮인_수량은_포지션_수량을_넘지_않는다() {
        PositionProtection protection = protection(List.of(
                AccountFixtures.stop(Direction.LONG, "58000"),
                AccountFixtures.stop(Direction.LONG, "57000")));

        assertThat(protection.stoppedQuantity()).isEqualTo(Quantity.of("0.1"));
    }

    /** 반대 방향의 주문은 이 포지션을 닫지 않는다. 담으면 숏의 손절이 롱을 보호하게 된다. */
    @Test
    void 다른_방향의_주문은_이_포지션의_보호가_아니다() {
        PositionProtection protection =
                PositionProtection.of(LONG, List.of(AccountFixtures.stop(Direction.SHORT, "62000")),
                        Optional.empty());

        assertThat(protection.orders()).isEmpty();
        assertThat(protection.coverage()).isEqualTo(StopLossCoverage.NONE);
    }

    @Test
    void 짝이_맞지_않는_주문을_직접_담으면_거부한다() {
        assertThatThrownBy(() -> new PositionProtection(
                LONG, List.of(AccountFixtures.stop(Direction.SHORT, "62000")), Optional.empty()))
                .isInstanceOf(InvalidAccountDataException.class)
                .hasMessageContaining("다른 포지션의 주문");
    }

    /** 표시가 60000, 계획 손절 58000, 수량 0.1 → 2000 × 0.1 = 200. */
    @Test
    void 덮이지_않은_수량이_계획_손절가까지_가면_잃는_돈을_낸다() {
        PositionProtection protection = new PositionProtection(
                LONG, List.of(), Optional.of(Price.of("58000")));

        assertThat(protection.exposureWithoutStop()).contains(Money.of("200.00"));
    }

    /** 계획이 없으면 어디까지 잃을지 말할 수 없다. 0 으로 채우면 "위험 없음" 으로 읽힌다. */
    @Test
    void 계획이_없으면_열려_있는_위험을_말할_수_없다() {
        assertThat(protection(List.of()).exposureWithoutStop()).isEmpty();
    }

    @Test
    void 전량이_덮여_있으면_열려_있는_위험이_없다() {
        PositionProtection protection = new PositionProtection(
                LONG, List.of(AccountFixtures.stop(Direction.LONG, "58000")),
                Optional.of(Price.of("58000")));

        assertThat(protection.exposureWithoutStop()).isEmpty();
    }

    private static PositionProtection protection(List<ProtectiveOrder> orders) {
        return PositionProtection.of(LONG, orders, Optional.empty());
    }
}
