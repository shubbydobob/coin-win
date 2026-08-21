package com.coinwin.position.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import com.coinwin.market.adapter.out.snapshot.ClasspathLeverageBracketAdapter;
import com.coinwin.market.application.service.LeverageBracketService;
import com.coinwin.market.domain.Symbol;
import com.coinwin.position.domain.Direction;
import com.coinwin.position.domain.MaintenanceMarginPolicy;
import com.coinwin.position.domain.PositionExposure;
import org.junit.jupiter.api.Test;

/**
 * Phase 3 완료 조건 — "Phase 1 청산가가 거래소 실제값과 오차 범위 내 일치".
 *
 * <p><b>여기서 말하는 '거래소 실제값' 이 무엇인지 밝혀 둔다.</b> 실제 포지션의 청산가를
 * 거래소에서 읽어 오려면 계좌 권한이 필요하고, 그것은 {@code scope.md} 가 배제한 범위다.
 * 그래서 대신 <b>바이낸스가 공개한 공식과 공개한 구간표</b>로 손으로 푼 값을 기준으로 삼는다.
 *
 * <pre>
 * LiqPrice = (WB + cumB - Side × Position × EP) / (Position × MMR - Side × Position)
 * </pre>
 *
 * <p>각 테스트에 대입 과정을 적었다. 코드에서 식을 옮겨 오지 않았으므로, 구현이 틀리면
 * 여기서 갈린다. 오차 허용치를 두지 않고 <b>센트 단위까지 정확히</b> 일치를 요구한다 —
 * 근사식을 버린 이유가 그 오차였기 때문이다.
 *
 * <p>구간표는 커밋된 스냅샷을 그대로 쓴다. 조립 전체가 한 번에 검사된다:
 * 스냅샷 → 어댑터 → 서비스 → 정책 → 청산가 공식.
 */
class LiquidationAgreesWithExchangeTest {

    private final MaintenanceMarginPolicy policy = new BracketMaintenanceMarginPolicy(
            new LeverageBracketService(new ClasspathLeverageBracketAdapter()), Symbol.BTC_USDT);

    private Price liquidationOf(Direction direction, String quantity, int leverage) {
        PositionExposure exposure = new PositionExposure(
                direction, Price.of("60000"), Quantity.of(quantity), leverage);
        return exposure.liquidationPrice(policy.requirementFor(exposure.notional()));
    }

    /**
     * 명목가 30,000 → 1구간 (MMR 0.4%, cumB 0).
     *
     * <pre>
     * WB = 0.5 × 60000 / 10 = 3000
     * (3000 + 0 - 0.5×60000) / (0.5×0.004 - 0.5) = (-27000) / (-0.498) = 54216.8674...
     * </pre>
     */
    @Test
    void 롱_1구간_청산가가_거래소_공식과_일치한다() {
        assertThat(liquidationOf(Direction.LONG, "0.5", 10)).isEqualTo(Price.of("54216.87"));
    }

    /**
     * 명목가 60,000 → 2구간 (MMR 0.5%, cumB 50). 구간이 바뀌면 값도 바뀌어야 한다.
     *
     * <pre>
     * WB = 1.0 × 60000 / 10 = 6000
     * (6000 + 50 - 60000) / (1×0.005 - 1) = (-53950) / (-0.995) = 54221.1055...
     * </pre>
     */
    @Test
    void 롱_2구간_청산가가_거래소_공식과_일치한다() {
        assertThat(liquidationOf(Direction.LONG, "1", 10)).isEqualTo(Price.of("54221.11"));
    }

    /**
     * <pre>
     * WB = 3000, Side = -1
     * (3000 + 0 + 0.5×60000) / (0.5×0.004 + 0.5) = 33000 / 0.502 = 65737.0517...
     * </pre>
     */
    @Test
    void 숏_1구간_청산가가_거래소_공식과_일치한다() {
        assertThat(liquidationOf(Direction.SHORT, "0.5", 10)).isEqualTo(Price.of("65737.05"));
    }

    /**
     * 같은 진입가·같은 레버리지인데 크기만 키우면 청산가가 <b>올라간다</b>(롱 기준).
     * 이것이 구간별 MMR 을 도입한 이유다. 고정 0.4% 였다면 두 값이 같게 나온다.
     */
    @Test
    void 포지션이_커지면_구간이_올라가고_청산이_앞당겨진다() {
        Price 작은것 = liquidationOf(Direction.LONG, "0.5", 10);
        Price 큰것 = liquidationOf(Direction.LONG, "1", 10);

        assertThat(큰것.isAbove(작은것)).isTrue();
    }

    /** 구간 상한은 포함이다. 명목가가 정확히 50,000 이면 아직 1구간이다. */
    @Test
    void 명목가가_정확히_구간_상한이면_아직_그_구간이다() {
        assertThat(policy.requirementFor(Money.of("50000")).deduction()).isEqualTo(Money.of("0"));
        assertThat(policy.requirementFor(Money.of("50000.01")).deduction())
                .isEqualTo(Money.of("50"));
    }
}
