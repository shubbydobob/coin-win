package com.coinwin.ai.application.port.in;

import com.coinwin.ai.domain.Narrative;
import com.coinwin.ai.domain.SummaryFacts;

/**
 * 수치 묶음을 문장으로 바꾼다.
 *
 * <p>부르는 쪽이 사실을 만들어 넘긴다. 이쪽이 백테스트를 직접 돌려 결과를 읽으면
 * {@code ai → backtest → ai} 순환이 되고 ArchUnit 규칙 3 이 빌드를 세운다. 그 제약이
 * 결과적으로 더 나은 모양을 만들었다 — 요약은 백테스트 전용이 아니게 됐다.
 */
public interface SummarizeUseCase {

    Narrative summarize(SummaryFacts facts);
}
