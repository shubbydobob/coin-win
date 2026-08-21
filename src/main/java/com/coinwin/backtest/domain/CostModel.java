package com.coinwin.backtest.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Percentage;
import com.coinwin.common.domain.Price;
import com.coinwin.position.domain.Direction;

/**
 * 수수료와 슬리피지. 주문 종류에 맞춰 나눠 둔다.
 *
 * <p><b>진입은 maker, 청산은 taker 다.</b> 진입가는 대 경계에 미리 걸어 두는 지정가라 호가를
 * 채우는 쪽이고, 손절·익절은 트리거 체결이라 호가를 걷어 가는 쪽이다. 양쪽을 taker 로 뭉개면
 * 지정가 진입의 이점이 결과에서 사라진다.
 *
 * <p>슬리피지는 <b>청산에만</b> 붙는다. 미리 걸어 둔 지정가는 그 가격에 체결되거나 안 되거나
 * 둘 중 하나다.
 *
 * <p><b>구체적인 요율은 여기 없다.</b> 어느 거래소가 얼마를 받는지는 도메인이 알 일이 아니고,
 * 요율을 상수로 박으면 {@code backtest} 가 특정 거래소에 묶인다 — Phase 6 완료 조건이
 * 금지하는 것이 정확히 그것이다. 기본값은 요청을 받는 가장자리({@code backtest/api})가 준다.
 *
 * @param makerFee 진입 수수료율
 * @param takerFee 청산 수수료율
 * @param slippage 청산 체결가가 불리한 쪽으로 밀리는 비율
 */
public record CostModel(Percentage makerFee, Percentage takerFee, Percentage slippage) {

    public CostModel {
        DomainValues.required(makerFee, "maker 수수료율");
        DomainValues.required(takerFee, "taker 수수료율");
        DomainValues.required(slippage, "슬리피지율");
    }

    /**
     * 비용 없는 모델.
     *
     * <p>같은 스펙을 이것으로 한 번 더 돌려 나란히 놓으면 <b>수수료가 엣지를 먹어 치우는지</b>가
     * 수치로 나온다. 전략이 이겼는데 계좌가 줄어드는 경우를 눈으로 보기 위한 것이다.
     */
    public static CostModel free() {
        Percentage zero = Percentage.of("0");
        return new CostModel(zero, zero, zero);
    }

    public Money entryFee(Money notional) {
        DomainValues.required(notional, "진입 명목가");
        return makerFee.applyTo(notional);
    }

    public Money exitFee(Money notional) {
        DomainValues.required(notional, "청산 명목가");
        return takerFee.applyTo(notional);
    }

    /**
     * 슬리피지를 반영한 청산 체결가. 언제나 <b>불리한 쪽</b>으로 민다.
     *
     * <p>롱을 닫는 것은 파는 것이므로 더 낮게, 숏을 닫는 것은 사는 것이므로 더 높게 체결된다.
     * 부호를 잘못 잡으면 백테스트가 실제보다 좋게 나오는데, 이 도구에서 허용되지 않는 방향의
     * 오차다.
     */
    public Price applyExitSlippage(Price price, Direction direction) {
        DomainValues.required(price, "청산가");
        DomainValues.required(direction, "포지션 방향");
        Money offset = slippage.applyTo(price.asAmount());
        return switch (direction) {
            case LONG -> price.minus(offset);
            case SHORT -> price.plus(offset);
        };
    }
}
