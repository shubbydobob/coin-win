package com.coinwin.backtest.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Money;

/**
 * 한 가지만 다른 두 실행을 나란히 놓은 것.
 *
 * <p>지표 필터가 값을 하는지, 수수료가 엣지를 먹어 치우는지는 <b>비교로만 답할 수 있는
 * 질문</b>이다. 한쪽 결과만 보면 숫자가 좋은지 나쁜지 말할 기준이 없다.
 *
 * @param baseline 기준 실행
 * @param variant 한 가지를 바꾼 실행
 */
public record BacktestComparison(BacktestResult baseline, BacktestResult variant) {

    public BacktestComparison {
        DomainValues.required(baseline, "기준 실행");
        DomainValues.required(variant, "비교 실행");
    }

    /** 바꾼 쪽이 벌어들인 차이. <b>음수면 그 변경이 손해</b>였다는 뜻이다. */
    public Money pnlDifference() {
        return variant.netPnl().minus(baseline.netPnl());
    }

    /** 바꾼 쪽에서 줄어든 거래 수. 필터를 켜면 양수, 끄면 음수다. */
    public int tradeDifference() {
        return variant.totalTrades() - baseline.totalTrades();
    }
}
