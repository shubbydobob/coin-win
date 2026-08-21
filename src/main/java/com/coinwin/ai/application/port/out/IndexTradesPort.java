package com.coinwin.ai.application.port.out;

import com.coinwin.ai.domain.TradeDocument;
import java.util.List;

/**
 * 거래 문서를 색인에 넣는다.
 *
 * <p>인덱스는 파생 데이터다. 진실의 원천은 매매 기록이고 여기 있는 것은 사본이므로,
 * 통째로 비워도 기록은 온전하고 언제든 다시 만들 수 있다. {@link #deleteAll()} 이 있는 이유가
 * 그것이다 — 문서 형식이나 임베딩 모델이 바뀌면 과거 기록이 구버전으로 남는다.
 */
public interface IndexTradesPort {

    /** 같은 식별자의 문서가 이미 있으면 덮어쓴다. */
    void save(List<TradeDocument> documents);

    void deleteAll();
}
