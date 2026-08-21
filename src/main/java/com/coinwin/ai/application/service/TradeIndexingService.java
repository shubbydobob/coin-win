package com.coinwin.ai.application.service;

import com.coinwin.ai.application.AiPorts;
import com.coinwin.ai.application.port.in.IndexTradesUseCase;
import com.coinwin.ai.application.port.out.IndexTradesPort;
import com.coinwin.ai.domain.TradeDocument;
import com.coinwin.journal.application.port.in.QueryJournalUseCase;
import com.coinwin.journal.domain.TradeId;
import com.coinwin.journal.domain.TradeQuery;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 매매 기록을 색인에 옮긴다.
 *
 * <p><b>거래 하나를 색인할 때도 전체를 읽는다.</b> 낭비처럼 보이지만 필요다 — 문서에 들어가는
 * "직전 거래는 손실이었다" 는 그 거래 하나만 봐서는 알 수 없고, 시간순 전체 목록 위에서만
 * 계산된다. 1인 사용자의 거래 수(수백~수천)에서 성립하는 선택이며, 이 전제가 깨지면
 * 직전 한 건만 함께 읽는 방식으로 좁혀야 한다.
 */
@Service
public class TradeIndexingService implements IndexTradesUseCase {

    private final QueryJournalUseCase journal;

    private final Optional<IndexTradesPort> index;

    public TradeIndexingService(QueryJournalUseCase journal, Optional<IndexTradesPort> index) {
        this.journal = journal;
        this.index = index;
    }

    @Override
    public int reindexAll() {
        List<TradeDocument> documents = documents();
        IndexTradesPort port = AiPorts.configured(index);
        // 지우고 다시 넣는다. 덮어쓰기만 하면 지금은 지워진 거래의 문서가 남는다.
        port.deleteAll();
        port.save(documents);
        return documents.size();
    }

    @Override
    public void index(TradeId id) {
        String target = id.value().toString();
        List<TradeDocument> one = documents().stream()
                .filter(document -> document.id().equals(target))
                .toList();
        if (!one.isEmpty()) {
            AiPorts.configured(index).save(one);
        }
    }

    private List<TradeDocument> documents() {
        return TradeDocument.over(journal.closedTrades(TradeQuery.all()));
    }
}
