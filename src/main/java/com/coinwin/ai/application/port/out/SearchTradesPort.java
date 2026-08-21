package com.coinwin.ai.application.port.out;

import com.coinwin.ai.domain.RetrievedTrade;
import java.util.List;

/**
 * 질문에 가까운 거래를 찾는다.
 *
 * <p>어댑터가 둘이다 — pgvector 와 인메모리. 계약 스위트 하나가 양쪽에서 통과해야 한다
 * ({@code LoadCandlesPort} 선례). 유사도 계산 방식은 어댑터마다 다르므로 <b>점수의 절대값이
 * 아니라 순서와 개수</b>만 계약이다.
 */
public interface SearchTradesPort {

    List<RetrievedTrade> search(String question, int topK);
}
