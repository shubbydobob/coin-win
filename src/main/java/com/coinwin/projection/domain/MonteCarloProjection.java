package com.coinwin.projection.domain;

import com.coinwin.common.domain.DomainValues;
import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * 같은 조건으로 N 번 돌린다. 기댓값이 아니라 <b>기댓값 주위의 흩어짐</b>을 보려는 것이다.
 *
 * <p>시드를 받는 이유는 결과가 재현 가능해야 하기 때문이다. 어제 본 숫자를 오늘 다시 만들 수
 * 없으면 두 계획을 비교할 수 없고, 비교할 수 없으면 이 도구는 난수 발생기에 지나지 않는다.
 */
public record MonteCarloProjection(ProjectionSpec spec, int runs, long seed) {

    private static final int MAXIMUM_RUNS = 10_000;

    public MonteCarloProjection {
        DomainValues.required(spec, "시뮬레이션 조건");
        DomainValues.atLeast(runs, 1, "시행 횟수");
        if (runs > MAXIMUM_RUNS) {
            throw new InvalidProjectionException("시행 횟수는 " + MAXIMUM_RUNS + " 을 넘을 수 없다: " + runs);
        }
    }

    /**
     * 난수원 하나를 모든 시행이 이어 쓴다. 시행마다 새로 만들면 같은 시드에서 같은 수열이
     * 다시 시작되어 N 번이 전부 같은 경로가 된다.
     */
    public ProjectionDistribution run() {
        RandomGenerator random = SeededRandom.of(seed);
        List<ProjectionOutcome> outcomes = new ArrayList<>(runs);
        for (int run = 0; run < runs; run++) {
            outcomes.add(ProjectionOutcome.of(spec.simulate(random)));
        }
        return new ProjectionDistribution(outcomes, spec.initialCapital());
    }
}
