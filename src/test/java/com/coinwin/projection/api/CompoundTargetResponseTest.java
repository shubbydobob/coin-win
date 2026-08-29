package com.coinwin.projection.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.coinwin.common.domain.ExchangeRate;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Percentage;
import com.coinwin.projection.domain.CompoundTarget;
import com.coinwin.projection.domain.TradingCost;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * 원화 묶음이 붙는 자리와 빠지는 자리.
 *
 * <p>HTTP 를 거치지 않고 직접 본다. 검증하려는 것이 <b>환율이 없을 때의 모양</b>이라
 * 컨텍스트가 필요 없고, 컨텍스트를 띄우면 어댑터를 갈아 끼우는 설정이 이 사실보다 커진다.
 */
class CompoundTargetResponseTest {

    private static final CompoundTarget TARGET = new CompoundTarget(
            Money.of("800"), Percentage.of("5"), 12,
            new TradingCost(
                    Percentage.of("0.05"), Percentage.of("0.02"), new BigDecimal("10"),
                    Percentage.of("100"), 20));

    @Test
    void 환율이_있으면_같은_금액을_원화로도_낸다() {
        ExchangeRate 환율 = new ExchangeRate(
                new BigDecimal("1370.00"), Instant.parse("2026-08-23T15:04:04Z"));

        var 응답 = CompoundTargetResponse.from(TARGET, Optional.of(환율));

        assertThat(응답.won().wonPerUsdt()).isEqualByComparingTo("1370.00");
        // 1436.69 × 1370 = 1,968,265.3 → 원에는 소수점이 없다
        assertThat(응답.won().finalEquity()).isEqualByComparingTo("1968265");
        assertThat(응답.won().totalProfit()).isEqualByComparingTo("872265");
        assertThat(응답.won().notional()).isEqualByComparingTo("10960000");
    }

    /** 0 원으로 채우면 화면이 그것을 금액으로 읽는다. 없는 것은 없어야 한다. */
    @Test
    void 환율이_없으면_원화_묶음이_통째로_빈다() {
        var 응답 = CompoundTargetResponse.from(TARGET, Optional.empty());

        assertThat(응답.won()).isNull();
        assertThat(응답.finalEquity()).isEqualByComparingTo("1436.69");
    }
}
