package com.coinwin.ai.application.port.out;

import com.coinwin.ai.domain.SummaryFacts;

/**
 * 수치를 문장으로 바꾼다.
 *
 * <p>문자열을 돌려줄 뿐 검사하지 않는다. 원본에 없는 수를 썼는지는 {@code Narrative} 가
 * 판정한다 — 어댑터가 판정까지 하면 모델 제공자마다 기준이 갈린다.
 */
public interface WriteSummaryPort {

    String write(SummaryFacts facts);
}
