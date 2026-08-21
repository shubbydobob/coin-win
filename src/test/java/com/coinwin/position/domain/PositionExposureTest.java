package com.coinwin.position.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.withPrecision;

import com.coinwin.common.domain.InvalidValueException;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Percentage;
import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import org.junit.jupiter.api.Test;

/**
 * 청산가 공식. Phase 3 완료 조건 "거래소 실제값과 오차 범위 내 일치" 가 걸린 자리다.
 *
 * <p>바이낸스 격리마진 공식은 다음과 같다.
 *
 * <pre>
 * LiqPrice = (WB + cumB - Side × Position × EP) / (Position × MMR - Side × Position)
 * </pre>
 *
 * <p>격리마진에서 {@code WB = Position × EP / leverage} 이므로 정리하면
 *
 * <pre>
 * LONG :  liq = [EP × (1 - 1/lev) - cumB/qty] / (1 - MMR)
 * SHORT:  liq = [EP × (1 + 1/lev) + cumB/qty] / (1 + MMR)
 * </pre>
 *
 * <p><b>핵심은 나눗셈이다.</b> 유지증거금은 진입 명목가가 아니라 <b>청산가 기준 명목가</b>로
 * 계산된다({@code qty × P × MMR}). Phase 1 의 근사식 {@code EP × (1 - 1/lev + MMR)} 은 그것을
 * 곱셈 한 번으로 뭉갠 것이고, 그래서 0.04% 대의 오차가 남았다.
 *
 * <p>기댓값은 아래 각 테스트에 손으로 푼 과정을 함께 적었다. 계산식을 코드에서 그대로 옮겨
 * 오면 공식이 틀렸을 때 테스트도 같이 틀린다.
 */
class PositionExposureTest {

    private static final Percentage MMR_04 = Percentage.of("0.4");
    private static final MaintenanceMargin BRACKET_1 =
            new MaintenanceMargin(MMR_04, Money.of("0"));

    private static PositionExposure exposure(Direction direction, String quantity) {
        return new PositionExposure(direction, Price.of("60000"), Quantity.of(quantity), 10);
    }

    /**
     * 검산: qty 0.5, EP 60000, lev 10 → 증거금 3000, 손실 0.5×(60000-P).
     * 유지증거금 0.5×P×0.004. 3000 - 0.5×(60000-P) = 0.5×P×0.004
     * → 6000 - 60000 + P = 0.004P → P×0.996 = 54000 → P = 54216.8674...
     */
    @Test
    void 롱_청산가는_바이낸스_공식과_일치한다() {
        Price liquidation = exposure(Direction.LONG, "0.5").liquidationPrice(BRACKET_1);

        assertThat(liquidation).isEqualTo(Price.of("54216.87"));
    }

    /**
     * 검산: 3000 - 0.5×(P-60000) = 0.5×P×0.004
     * → 6000 + 60000 - P = 0.004P → P×1.004 = 66000 → P = 65737.0517...
     */
    @Test
    void 숏_청산가는_바이낸스_공식과_일치한다() {
        Price liquidation = exposure(Direction.SHORT, "0.5").liquidationPrice(BRACKET_1);

        assertThat(liquidation).isEqualTo(Price.of("65737.05"));
    }

    /**
     * Phase 1 근사식이 남기던 오차를 수치로 못 박는다. 근사식은 54240.00 을 냈다.
     * 롱에서 근사식이 <b>더 높은</b> 청산가를 내므로 실제보다 위험해 보이는 쪽이었지만,
     * 그래도 23 USDT 는 손절가를 어디 둘지 판단할 때 무시할 크기가 아니다.
     */
    @Test
    void 근사식과의_차이는_0_05퍼센트_안쪽이지만_23USDT다() {
        double 정확 = exposure(Direction.LONG, "0.5").liquidationPrice(BRACKET_1).value()
                .doubleValue();
        double 근사 = 60000 * (1 - 1.0 / 10 + 0.004);

        assertThat(근사 - 정확).isCloseTo(23.13, withPrecision(0.01));
        assertThat((근사 - 정확) / 정확 * 100).isCloseTo(0.0427, withPrecision(0.001));
    }

    /**
     * 유지증거금 공제액이 붙는 2구간. 검산: qty 1.0, cumB 50, MMR 0.5%
     * P×(1-0.005) = 60000×0.9 - 50/1.0 = 53950 → P = 53950/0.995 = 54221.1055...
     */
    @Test
    void 공제액이_있는_구간에서는_공제액이_청산가를_낮춘다() {
        MaintenanceMargin bracket2 = new MaintenanceMargin(Percentage.of("0.5"), Money.of("50"));

        Price liquidation = new PositionExposure(
                Direction.LONG, Price.of("60000"), Quantity.of("1"), 10)
                .liquidationPrice(bracket2);

        assertThat(liquidation).isEqualTo(Price.of("54221.11"));
    }

    /** 같은 진입가라도 포지션이 크면 더 높은 MMR 구간에 들어 더 빨리 청산된다. */
    @Test
    void 큰_포지션일수록_롱_청산가가_높아진다() {
        Price 작은것 = exposure(Direction.LONG, "0.5").liquidationPrice(BRACKET_1);
        Price 큰것 = new PositionExposure(Direction.LONG, Price.of("60000"), Quantity.of("1"), 10)
                .liquidationPrice(new MaintenanceMargin(Percentage.of("0.5"), Money.of("50")));

        assertThat(큰것.isAbove(작은것)).isTrue();
    }

    @Test
    void 레버리지가_높을수록_롱_청산가가_진입가에_가깝다() {
        Price 레버리지10 = exposure(Direction.LONG, "0.5").liquidationPrice(BRACKET_1);
        Price 레버리지20 = new PositionExposure(
                Direction.LONG, Price.of("60000"), Quantity.of("0.5"), 20)
                .liquidationPrice(BRACKET_1);

        assertThat(레버리지20.isAbove(레버리지10)).isTrue();
    }

    @Test
    void 명목가는_수량_곱하기_평단이다() {
        assertThat(exposure(Direction.LONG, "0.5").notional()).isEqualTo(Money.of("30000"));
    }

    /** 수량 0 인 포지션의 청산가는 성립하지 않는다. 공제액을 수량으로 나눠야 하기 때문이다. */
    @Test
    void 수량이_0이면_거부된다() {
        assertThatThrownBy(() -> exposure(Direction.LONG, "0"))
                .isInstanceOf(InvalidPositionPlanException.class);
    }

    @Test
    void 레버리지는_1_이상이어야_한다() {
        assertThatThrownBy(() -> new PositionExposure(
                Direction.LONG, Price.of("60000"), Quantity.of("0.5"), 0))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    void null은_거부된다() {
        assertThatThrownBy(() -> new PositionExposure(null, Price.of("1"), Quantity.of("1"), 1))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new PositionExposure(Direction.LONG, null, Quantity.of("1"), 1))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new PositionExposure(Direction.LONG, Price.of("1"), null, 1))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> exposure(Direction.LONG, "0.5").liquidationPrice(null))
                .isInstanceOf(InvalidValueException.class);
    }
}
