-- 매매 기록 RAG 인덱스.
--
-- 이 테이블은 파생 데이터다. 진실의 원천은 trade 테이블이고, 여기 있는 것은 거래 하나를
-- 문장으로 렌더링한 사본과 그 임베딩이다. 통째로 비워도 매매 기록은 온전하며 재색인으로
-- 언제든 다시 만들 수 있다. 그래서 색인 실패는 장애가 아니다.
--
-- 컬럼 이름과 타입은 Spring AI 의 PgVectorStore 가 기대하는 그대로다. 스키마를 만드는 쪽만
-- Flyway 로 가져왔다(spring.ai.vectorstore.pgvector.initialize-schema: false).
-- 마이그레이션이 유일한 스키마 출처라는 전제를 라이브러리 하나 때문에 깨지 않는다.
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE vector_store (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content   TEXT,
    metadata  JSON,
    -- 1536 은 text-embedding-3-small 의 차원이다. application.yml 의
    -- spring.ai.vectorstore.pgvector.dimensions 와 반드시 같아야 한다. 임베딩 모델을 바꾸면
    -- 이 숫자도 바뀌고, 그때는 마이그레이션과 전체 재색인이 함께 필요하다 —
    -- 차원이 다른 벡터가 한 테이블에 섞이면 검색은 예외 없이 조용히 망가진다.
    embedding VECTOR(1536)
);

-- 코사인 거리 기준 HNSW. 1인 사용자의 거래 수(수백~수천)에서는 인덱스 없이도 빠르지만,
-- 있을 때와 없을 때 결과 순서가 갈릴 여지를 남기지 않는다.
CREATE INDEX vector_store_embedding_idx ON vector_store USING hnsw (embedding vector_cosine_ops);
