package com.coinwin.ai.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.journal.domain.ClosedTrade;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * 검색되는 거래 하나. 문장 하나와, 거르는 데 쓸 값들.
 *
 * <p><b>파생 사실이 이 타입의 존재 이유다.</b> ADR 005 가 RAG 의 예로 든 "손실 직후 진입한
 * 거래의 결과" 는 의미 검색으로 찾을 수 없다 — 유사도는 문장이 서로 얼마나 닮았는지만 보고,
 * "직후" 는 <b>순서</b>에서만 나온다. 그래서 색인 시점에 미리 계산해 문장과 메타데이터
 * 양쪽에 박는다. 문장에 넣는 것은 의미 검색이 걸리게 하기 위해서고, 메타데이터에 넣는 것은
 * 나중에 조건으로 거르기 위해서다.
 *
 * <p>그래서 {@link #over(List)} 는 목록을 받는다. 거래 하나만 보고는 그것이 손실 직후였는지
 * 알 수 없다. 목록은 <b>진입 시각 오름차순</b>이어야 한다 — {@code LoadTradesPort.findClosed}
 * 의 계약이 그렇다.
 */
public record TradeDocument(String id, String content, Map<String, Object> metadata) {

    public TradeDocument {
        DomainValues.required(id, "문서 식별자");
        DomainValues.required(content, "문서 본문");
        metadata = Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }

    /** 시간순 거래 목록을 문서 목록으로. 순서는 그대로 유지된다. */
    public static List<TradeDocument> over(List<ClosedTrade> ordered) {
        DomainValues.required(ordered, "시간순 거래 목록");
        return IntStream.range(0, ordered.size())
                .mapToObj(index -> of(ordered.get(index), previousOf(ordered, index)))
                .toList();
    }

    private static Optional<ClosedTrade> previousOf(List<ClosedTrade> ordered, int index) {
        return index == 0 ? Optional.empty() : Optional.of(ordered.get(index - 1));
    }

    private static TradeDocument of(ClosedTrade trade, Optional<ClosedTrade> previous) {
        return new TradeDocument(
                trade.id().value().toString(),
                TradeSentences.describe(trade, previous),
                metadataOf(trade, previous));
    }

    private static Map<String, Object> metadataOf(
            ClosedTrade trade, Optional<ClosedTrade> previous) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("tradeId", trade.id().value().toString());
        metadata.put("direction", trade.plan().direction().name());
        metadata.put("leverage", trade.plan().leverage());
        metadata.put("followedPlan", trade.followedPlan());
        metadata.put("exitReason", trade.closure().reason().name());
        metadata.put("realizedPnl", trade.realizedPnl().value());
        metadata.put("costOfDeviation", trade.costOfDeviation().value());
        metadata.put("openedAt", trade.openedAt().toString());
        metadata.put("closedAt", trade.closedAt().toString());
        metadata.put("holdingMinutes", trade.holdingPeriod().toMinutes());
        metadata.put("ichimoku", trade.context().ichimokuPosition().name());
        metadata.put("bollinger", trade.context().bollingerPosition().name());
        putSequenceFacts(metadata, trade, previous);
        return metadata;
    }

    /**
     * 직전 거래와의 관계. <b>첫 거래에는 키 자체가 없다</b> — 없는 것을 {@code false} 로 적으면
     * "손실 직후가 아니었다" 는 거짓말이 되고, 그 거짓말은 조건 검색에 그대로 걸린다.
     */
    private static void putSequenceFacts(
            Map<String, Object> metadata, ClosedTrade trade, Optional<ClosedTrade> previous) {
        previous.filter(trade::opensAfter).ifPresent(before -> {
            metadata.put("afterLoss", before.realizedPnl().value().signum() < 0);
            metadata.put("minutesSincePreviousTrade",
                    trade.timeSincePreviousTrade(before).toMinutes());
        });
    }
}
