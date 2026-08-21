package com.coinwin.ai.adapter.in.event;

import com.coinwin.ai.application.port.in.IndexTradesUseCase;
import com.coinwin.journal.application.TradeClosedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 거래가 닫히면 색인에 반영한다.
 *
 * <p><b>실패를 삼킨다.</b> 색인은 파생이고 진실의 원천은 매매 기록이므로, OpenAI 가 닿지
 * 않는다고 청산 기록이 실패하면 안 된다. 잃는 것은 그 거래가 검색에 잡히지 않는 것뿐이고
 * 복구는 재색인 한 번이다.
 *
 * <p>동기로 돈다. 청산 응답이 임베딩 한 번만큼 느려지는 대가를 받아들였다 — 비동기로 만들면
 * 실행 순서가 테스트에서 보이지 않게 되고, 1인 사용자의 청산 빈도에서 그 복잡도가 값을 하지
 * 않는다. 이 전제가 깨지면(자동 매매나 다중 사용자) 여기부터 다시 봐야 한다.
 */
@Component
class TradeClosedIndexListener {

    private static final Logger LOG = LoggerFactory.getLogger(TradeClosedIndexListener.class);

    private final IndexTradesUseCase indexTrades;

    TradeClosedIndexListener(IndexTradesUseCase indexTrades) {
        this.indexTrades = indexTrades;
    }

    @EventListener
    void onTradeClosed(TradeClosedEvent event) {
        try {
            indexTrades.index(event.id());
        } catch (RuntimeException failure) {
            LOG.warn("거래 {} 를 색인하지 못했다. 기록은 저장됐고 재색인으로 복구된다.",
                    event.id(), failure);
        }
    }
}
