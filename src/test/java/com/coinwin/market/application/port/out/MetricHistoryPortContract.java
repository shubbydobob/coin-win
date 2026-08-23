package com.coinwin.market.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;

import com.coinwin.market.domain.MetricHistory;
import com.coinwin.market.domain.Symbol;
import org.junit.jupiter.api.Test;

/**
 * 지표 이력 포트의 계약. 바이낸스 어댑터와 인메모리 어댑터가 모두 통과해야 한다.
 *
 * <p>여기서도 값을 단언하지 않는다. 확인하는 것은 <b>표본 수가 요청을 넘지 않는다</b> 는 것과
 * <b>세 지표가 서로 독립적으로 읽힌다</b> 는 것 둘이다. 뒤쪽이 이 포트를 셋으로 나눈 이유다.
 */
public abstract class MetricHistoryPortContract {

    protected static final Symbol SYMBOL = Symbol.of("BTCUSDT");

    protected abstract LoadMetricHistoryPort port();

    @Test
    void 표본_수는_요청한_수를_넘지_않는다() {
        assertThat(port().fundingRates(SYMBOL, 10).sampleCount()).isBetween(1, 10);
        assertThat(port().openInterest(SYMBOL, 10).sampleCount()).isBetween(1, 10);
        assertThat(port().longShortRatios(SYMBOL, 10).sampleCount()).isBetween(1, 10);
    }

    @Test
    void 세_지표는_따로_읽힌다() {
        MetricHistory funding = port().fundingRates(SYMBOL, 5);
        MetricHistory openInterest = port().openInterest(SYMBOL, 5);

        assertThat(funding.samples()).isNotEmpty();
        assertThat(openInterest.samples()).isNotEmpty();
    }

    /** 미결제약정과 롱숏비율은 <b>언제나 양수</b>다. 0 이나 음수가 오면 매핑이 어긋난 것이다. */
    @Test
    void 미결제약정과_롱숏비율은_양수다() {
        assertThat(port().openInterest(SYMBOL, 10).samples())
                .allSatisfy(sample -> assertThat(sample.signum()).isPositive());
        assertThat(port().longShortRatios(SYMBOL, 10).samples())
                .allSatisfy(sample -> assertThat(sample.signum()).isPositive());
    }
}
