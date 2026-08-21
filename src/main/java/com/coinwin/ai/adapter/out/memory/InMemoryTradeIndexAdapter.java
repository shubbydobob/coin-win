package com.coinwin.ai.adapter.out.memory;

import com.coinwin.ai.application.port.out.IndexTradesPort;
import com.coinwin.ai.application.port.out.SearchTradesPort;
import com.coinwin.ai.domain.RetrievedTrade;
import com.coinwin.ai.domain.TradeDocument;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * DB 도 임베딩도 없이 도는 색인. 테스트와 로컬 실행용이다.
 *
 * <p><b>의미 검색이 아니다.</b> 낱말이 겹치는 정도로 순위를 매긴다. 그럼에도 포트 계약을
 * 만족하는 이유는 계약이 <b>순서와 개수와 덮어쓰기</b>이지 유사도 계산 방식이 아니기 때문이다.
 * 같은 계약 스위트가 pgvector 어댑터에서도 통과해야 한다 — {@code LoadCandlesPort} 선례다.
 *
 * <p>이것이 운영에서 쓰이는 일은 없다. 키가 있으면 pgvector 어댑터가 뜨고, 키가 없으면
 * 검색 기능 자체가 503 이다.
 */
public class InMemoryTradeIndexAdapter implements IndexTradesPort, SearchTradesPort {

    private final Map<String, TradeDocument> documents = new LinkedHashMap<>();

    @Override
    public void save(List<TradeDocument> incoming) {
        incoming.forEach(document -> documents.put(document.id(), document));
    }

    @Override
    public void deleteAll() {
        documents.clear();
    }

    @Override
    public List<RetrievedTrade> search(String question, int topK) {
        Set<String> asked = wordsOf(question);
        return documents.values().stream()
                .map(document -> scored(document, asked))
                .filter(retrieved -> retrieved.score() > 0)
                .sorted(Comparator.comparingDouble(RetrievedTrade::score).reversed())
                .limit(topK)
                .toList();
    }

    private static RetrievedTrade scored(TradeDocument document, Set<String> asked) {
        Set<String> words = wordsOf(document.content());
        long overlap = asked.stream().filter(words::contains).count();
        return new RetrievedTrade(document.id(), (double) overlap, document.content());
    }

    private static Set<String> wordsOf(String text) {
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^0-9a-z가-힣]+"))
                .filter(word -> !word.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }
}
