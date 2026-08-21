package com.coinwin.projection.domain;

import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

/**
 * 시드 하나가 결과 하나를 결정한다.
 *
 * <p>알고리즘 이름을 박아 두는 이유는 재현성 때문이다. 기본 구현에 맡기면 JDK 가 바뀌는 순간
 * 같은 시드가 다른 수열을 내고, 어제 본 시뮬레이션을 오늘 다시 만들 수 없다. 결과를 비교하는
 * 도구에서 그것은 도구 자체를 못 쓰게 만드는 결함이다.
 */
final class SeededRandom {

    private static final String ALGORITHM = "L64X128MixRandom";

    private SeededRandom() {
    }

    static RandomGenerator of(long seed) {
        return RandomGeneratorFactory.of(ALGORITHM).create(seed);
    }
}
