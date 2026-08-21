package com.coinwin.position.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Percentage;

/**
 * 유지증거금률을 포지션 크기와 무관한 하나의 값으로 본다.
 *
 * <p>Phase 3 이후로 이것은 <b>테스트용 구현</b>이다. 실제 조립은 구간표를 쓰는
 * {@code BracketMaintenanceMarginPolicy} 가 맡는다. 그래도 남겨 두는 이유는, 도메인 테스트가
 * MMR 을 직접 주입해서 "공식이 맞는가" 만 보게 하기 위해서다. 구간표를 끌고 들어오면 공식이
 * 틀렸는지 구간 선택이 틀렸는지 구분되지 않는다.
 */
public record FixedMaintenanceMarginPolicy(Percentage rate) implements MaintenanceMarginPolicy {

    /**
     * BTCUSDT 무기한 선물 1구간(명목가 50,000 USDT 이하)의 유지증거금률 0.4%.
     *
     * <p>Phase 1 에서는 이 값이 <b>모든 크기에 대한 근사치</b>였다. Phase 3 부터는 1구간에
     * 한해 정확한 값이고, 그보다 큰 포지션에서는 여전히 근사치다. 이름을 그대로 둔 이유가
     * 이것이다 — 어디까지가 정확한지 헷갈릴 여지를 남기지 않는다.
     */
    public static final Percentage BTC_USDT_APPROXIMATE_RATE = Percentage.of("0.4");

    public FixedMaintenanceMarginPolicy {
        DomainValues.required(rate, "유지증거금률");
    }

    public static FixedMaintenanceMarginPolicy btcUsdtApproximation() {
        return new FixedMaintenanceMarginPolicy(BTC_USDT_APPROXIMATE_RATE);
    }

    /** 명목가를 보지 않는다. 그것이 이 구현이 근사치인 이유 그 자체다. */
    @Override
    public MaintenanceMargin requirementFor(Money notional) {
        return MaintenanceMargin.flatRate(rate);
    }
}
