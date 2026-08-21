package com.coinwin.position.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Percentage;

/**
 * 유지증거금률을 하나의 고정값으로 본다. Phase 1 의 임시 구현이다.
 *
 * <p>실제 바이낸스 MMR 은 포지션 명목가 구간마다 달라진다({@code /fapi/v1/leverageBracket}).
 * 그 값을 가져오려면 {@code market} 모듈과 HTTP 어댑터가 필요하고 둘 다 Phase 3 에 있다.
 */
public record FixedMaintenanceMarginPolicy(Percentage rate) implements MaintenanceMarginPolicy {

    /**
     * BTCUSDT 무기한 선물 유지증거금률의 <b>근사치</b> 0.4%.
     *
     * <p>정확한 값이 아니다. 이름과 이 문서로 근사치임을 드러내는 이유는, 상수를 그냥
     * {@code 0.004} 로 흘려두면 Phase 3 에서 실제값과 어긋났을 때 원인이 "공식이 틀렸다" 인지
     * "MMR 입력이 다르다" 인지 구분되지 않기 때문이다.
     */
    public static final Percentage BTC_USDT_APPROXIMATE_RATE = Percentage.of("0.4");

    public FixedMaintenanceMarginPolicy {
        DomainValues.required(rate, "유지증거금률");
    }

    public static FixedMaintenanceMarginPolicy btcUsdtApproximation() {
        return new FixedMaintenanceMarginPolicy(BTC_USDT_APPROXIMATE_RATE);
    }
}
