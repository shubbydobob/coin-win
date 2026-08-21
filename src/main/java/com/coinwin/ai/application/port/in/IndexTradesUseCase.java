package com.coinwin.ai.application.port.in;

import com.coinwin.journal.domain.TradeId;

/** 매매 기록을 검색 가능한 상태로 만든다. */
public interface IndexTradesUseCase {

    /**
     * 전부 다시 만든다. 문서 형식이나 임베딩 모델이 바뀌었을 때 쓴다.
     *
     * @return 색인된 문서 수
     */
    int reindexAll();

    /** 거래 하나를 색인에 반영한다. 청산 직후에 불린다. */
    void index(TradeId id);
}
