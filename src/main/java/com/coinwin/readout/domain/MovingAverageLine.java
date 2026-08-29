package com.coinwin.readout.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Price;
import com.coinwin.indicator.domain.IndicatorPoint;
import java.util.List;

/**
 * 이동평균선 하나. 구간과 점들을 함께 든다.
 *
 * <p><b>지도({@code Map}) 가 아니라 목록인 이유는 순서다.</b> 처음에는
 * {@code Map<Integer, ...>} 로 들었는데 {@code Map.copyOf} 가 <b>순서를 보존하지 않는다</b> —
 * {@code LinkedHashMap} 으로 만들어 넣어도 사본은 순서가 없다. 그래서 20·50·200 이 응답마다
 * 다른 차례로 나왔고, 화면은 색을 구간이 아니라 자리로 고르고 있었으므로 <b>새로고침할 때마다
 * 선 색이 바뀌었을 것이다.</b>
 *
 * <p>컨트롤러 테스트가 그것을 잡았다. 값은 전부 맞았고 순서만 흔들리는 종류라 눈으로는
 * 알기 어렵다.
 */
public record MovingAverageLine(int period, List<IndicatorPoint<Price>> points) {

    public MovingAverageLine {
        DomainValues.atLeast(period, 1, "이동평균 기간");
        points = List.copyOf(points);
    }
}
