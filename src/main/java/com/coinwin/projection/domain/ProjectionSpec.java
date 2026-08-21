package com.coinwin.projection.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.InvalidValueException;
import com.coinwin.common.domain.Money;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * 시뮬레이션 조건 — 초기 자본, 매매 우위, 거래 빈도. 여기서 자산 곡선이 나온다.
 */
public record ProjectionSpec(Money initialCapital, TradingEdge edge, TradeFrequency frequency) {

    public ProjectionSpec {
        DomainValues.required(initialCapital, "초기 자본");
        DomainValues.required(edge, "매매 우위");
        DomainValues.required(frequency, "거래 빈도");
        if (initialCapital.value().signum() <= 0) {
            throw new InvalidValueException(
                    "초기 자본은 0 보다 커야 한다: " + initialCapital.value().toPlainString());
        }
    }

    /**
     * 확정된 승패 순서 하나에 대한 자산 곡선.
     *
     * <p>각 점은 직전 점이 아니라 <b>초기 자본에</b> 누적 배수를 곱해 얻는다. 점마다 센트로
     * 반올림한 값을 다시 곱하면 오차가 거래 수만큼 쌓이고, 그러면 승패 구성이 같고 순서만
     * 다른 두 경로의 최종 자산이 갈라진다. 그것은 복리의 성질이 아니라 반올림의 잔재다.
     */
    public EquityCurve project(List<TradeOutcome> outcomes) {
        DomainValues.required(outcomes, "거래 결과");
        List<Money> points = new ArrayList<>(outcomes.size() + 1);
        points.add(initialCapital);
        BigDecimal cumulative = BigDecimal.ONE;
        for (TradeOutcome outcome : outcomes) {
            cumulative = cumulative.multiply(edge.factorFor(outcome), MathContext.DECIMAL128);
            points.add(initialCapital.times(cumulative));
        }
        return new EquityCurve(points);
    }

    /** 시드가 정한 승패 순서로 만든 표본 경로 하나. 같은 시드는 같은 곡선을 낸다. */
    public EquityCurve simulate(long seed) {
        return simulate(SeededRandom.of(seed));
    }

    /**
     * 넘겨받은 난수원으로 한 번의 경로를 만든다.
     *
     * <p>몬테카를로가 시행마다 난수원을 새로 만들지 않고 하나를 이어 쓰기 위해 열어 둔
     * 통로다. 시행마다 새로 만들면 모든 시행이 같은 경로가 된다.
     */
    EquityCurve simulate(RandomGenerator random) {
        List<TradeOutcome> outcomes = new ArrayList<>(frequency.totalTrades());
        for (int trade = 0; trade < frequency.totalTrades(); trade++) {
            outcomes.add(edge.drawOutcome(random));
        }
        return project(outcomes);
    }
}
