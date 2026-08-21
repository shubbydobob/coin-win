package com.coinwin.ai.adapter.out.pgvector;

import com.coinwin.ai.application.port.out.IndexTradesPort;
import com.coinwin.ai.application.port.out.SearchTradesPort;
import com.coinwin.ai.domain.RetrievedTrade;
import com.coinwin.ai.domain.TradeDocument;
import java.util.List;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * 임베딩으로 거래를 찾는다.
 *
 * <p>문서 식별자를 거래 식별자로 쓴다. 같은 거래를 다시 색인하면 새 행이 생기지 않고 덮어써야
 * 하기 때문이다 — 스토어가 만든 식별자를 쓰면 청산 한 건이 색인될 때마다 사본이 쌓인다.
 *
 * <p>{@link #deleteAll()} 만 스토어 API 가 아니라 SQL 이다. 벡터 스토어의 삭제는 식별자
 * 목록이나 필터식을 요구하는데, "전부" 를 뜻하는 필터식을 지어내는 것보다 우리가 만든 테이블을
 * 우리가 비우는 편이 정직하다({@code V3__vector_store.sql}).
 */
@Component
@ConditionalOnProperty(name = "spring.ai.vectorstore.type", havingValue = "pgvector",
        matchIfMissing = true)
class PgVectorTradeIndexAdapter implements IndexTradesPort, SearchTradesPort {

    private final VectorStore vectorStore;

    private final JdbcClient jdbc;

    PgVectorTradeIndexAdapter(VectorStore vectorStore, JdbcClient jdbc) {
        this.vectorStore = vectorStore;
        this.jdbc = jdbc;
    }

    @Override
    public void save(List<TradeDocument> documents) {
        if (documents.isEmpty()) {
            // 빈 목록으로 임베딩 API 를 부르지 않는다.
            return;
        }
        vectorStore.add(documents.stream().map(PgVectorTradeIndexAdapter::toDocument).toList());
    }

    @Override
    public void deleteAll() {
        jdbc.sql("DELETE FROM vector_store").update();
    }

    @Override
    public List<RetrievedTrade> search(String question, int topK) {
        List<Document> found = vectorStore.similaritySearch(
                SearchRequest.builder().query(question).topK(topK).build());
        return found == null ? List.of() : found.stream()
                .map(PgVectorTradeIndexAdapter::toRetrieved)
                .toList();
    }

    private static Document toDocument(TradeDocument document) {
        return new Document(document.id(), document.content(), document.metadata());
    }

    private static RetrievedTrade toRetrieved(Document document) {
        Double score = document.getScore();
        return new RetrievedTrade(document.getId(), score == null ? 0 : score, document.getText());
    }
}
