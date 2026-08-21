package com.coinwin.ai;

import java.util.List;
import java.util.Locale;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/**
 * 낱말을 해시로 흩뿌려 만든 임베딩. <b>키 없이 pgvector 어댑터를 검증하기 위한 것이다.</b>
 *
 * <p>OpenAI 를 부르지 않으므로 통합 테스트가 네트워크·비용·모델 변덕에 묶이지 않는다. 그러면서
 * 검증되는 것은 적지 않다 — SQL, 식별자로 덮어쓰기, 비우기, topK, 차원(1536) 일치가 전부
 * 실제 PostgreSQL 위에서 확인된다. 검증되지 않는 것은 <b>임베딩의 품질</b> 하나뿐이고,
 * 그것은 어차피 어떤 자동 테스트로도 고정할 수 없다.
 *
 * <p>같은 낱말이 같은 칸에 더해지므로 문장이 겹칠수록 벡터가 가까워진다 — 유사도가 완전히
 * 무의미하지는 않다는 뜻이다. 그래도 이것을 의미 검색이라고 부르지는 않는다.
 */
public class DeterministicEmbeddingModel implements EmbeddingModel {

    /** V3__vector_store.sql 의 vector(1536) 과 같아야 한다. */
    public static final int DIMENSIONS = 1536;

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> embeddings = java.util.stream.IntStream
                .range(0, request.getInstructions().size())
                .mapToObj(index -> new Embedding(
                        vectorOf(request.getInstructions().get(index)), index))
                .toList();
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        return vectorOf(document.getText() == null ? "" : document.getText());
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    private static float[] vectorOf(String text) {
        float[] vector = new float[DIMENSIONS];
        for (String word : text.toLowerCase(Locale.ROOT).split("[^0-9a-z가-힣]+")) {
            if (!word.isBlank()) {
                vector[Math.floorMod(word.hashCode(), DIMENSIONS)] += 1f;
            }
        }
        return normalized(vector);
    }

    /** 코사인 거리를 쓰므로 길이를 1 로 맞춘다. 0 벡터는 그대로 둔다 — 나누면 NaN 이 된다. */
    private static float[] normalized(float[] vector) {
        double length = Math.sqrt(sumOfSquares(vector));
        if (length == 0) {
            vector[0] = 1f;
            return vector;
        }
        for (int i = 0; i < vector.length; i++) {
            vector[i] = (float) (vector[i] / length);
        }
        return vector;
    }

    private static double sumOfSquares(float[] vector) {
        double total = 0;
        for (float value : vector) {
            total += (double) value * value;
        }
        return total;
    }
}
