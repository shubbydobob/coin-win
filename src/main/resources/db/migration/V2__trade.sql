-- 매매 기록 테이블.
--
-- 한 거래가 세 상태(계획 / 체결 / 청산)를 지나므로 상태에 따라 비어 있는 칸이 생긴다.
-- 도메인은 그것을 세 타입으로 나눠 표현하지만 관계형 테이블에는 상속이 없으므로, 대신
-- 아래 CHECK 제약이 "상태와 채워진 칸이 어긋나지 않는다" 를 강제한다. 도메인 불변식의
-- 되풀이가 아니라 이중 방어다 — 어댑터를 거치지 않는 경로(수동 보정, 마이그레이션)가 생긴다.
CREATE TABLE trade (
    id                 UUID           NOT NULL,
    state              VARCHAR(10)    NOT NULL,

    -- 계획. 세 상태 모두가 갖는다.
    direction          VARCHAR(5)     NOT NULL,
    leverage           INTEGER        NOT NULL,
    stop_loss          NUMERIC(20, 2) NOT NULL,
    take_profit        NUMERIC(20, 2) NOT NULL,
    planned_at         TIMESTAMPTZ    NOT NULL,

    -- 진입 시점의 시장 상태. 체결된 뒤에만 존재한다.
    price_at_entry     NUMERIC(20, 2),
    ichimoku_position  VARCHAR(6),
    bollinger_position VARCHAR(6),
    rationale          VARCHAR(500),

    -- 청산. 닫힌 뒤에만 존재한다.
    exit_price         NUMERIC(20, 2),
    exit_at            TIMESTAMPTZ,
    exit_reason        VARCHAR(20),
    fees               NUMERIC(20, 2),
    funding            NUMERIC(20, 2),

    CONSTRAINT trade_pk PRIMARY KEY (id),
    CONSTRAINT trade_state_known CHECK (state IN ('PLANNED', 'OPEN', 'CLOSED')),
    CONSTRAINT trade_leverage_positive CHECK (leverage >= 1),
    CONSTRAINT trade_fees_non_negative CHECK (fees IS NULL OR fees >= 0),

    -- 계획 상태에는 진입 맥락이 없고, 체결된 뒤에는 반드시 있다.
    CONSTRAINT trade_context_matches_state CHECK (
        (state = 'PLANNED'
            AND price_at_entry IS NULL AND ichimoku_position IS NULL
            AND bollinger_position IS NULL AND rationale IS NULL)
        OR (state <> 'PLANNED'
            AND price_at_entry IS NOT NULL AND ichimoku_position IS NOT NULL
            AND bollinger_position IS NOT NULL AND rationale IS NOT NULL)),

    -- 청산 칸은 전부 있거나 전부 없다. 손익이 반쯤 적힌 기록은 집계에 쓸 수 없다.
    CONSTRAINT trade_closure_matches_state CHECK (
        (state <> 'CLOSED'
            AND exit_price IS NULL AND exit_at IS NULL AND exit_reason IS NULL
            AND fees IS NULL AND funding IS NULL)
        OR (state = 'CLOSED'
            AND exit_price IS NOT NULL AND exit_at IS NOT NULL AND exit_reason IS NOT NULL
            AND fees IS NOT NULL AND funding IS NOT NULL))
);

-- 분할 진입 계획. seq 가 곧 진입 순서다.
CREATE TABLE trade_planned_entry (
    trade_id   UUID           NOT NULL,
    seq        INTEGER        NOT NULL,
    price      NUMERIC(20, 2) NOT NULL,
    allocation NUMERIC(10, 4) NOT NULL,

    CONSTRAINT trade_planned_entry_pk PRIMARY KEY (trade_id, seq),
    CONSTRAINT trade_planned_entry_trade_fk FOREIGN KEY (trade_id)
        REFERENCES trade (id) ON DELETE CASCADE,
    CONSTRAINT trade_planned_entry_allocation_positive CHECK (allocation > 0)
);

-- 실제 진입 체결. seq 가 곧 체결 순서이며, 그 순서가 평단의 변천사다.
CREATE TABLE trade_fill (
    trade_id  UUID           NOT NULL,
    seq       INTEGER        NOT NULL,
    price     NUMERIC(20, 2) NOT NULL,
    quantity  NUMERIC(30, 8) NOT NULL,
    filled_at TIMESTAMPTZ    NOT NULL,

    CONSTRAINT trade_fill_pk PRIMARY KEY (trade_id, seq),
    CONSTRAINT trade_fill_trade_fk FOREIGN KEY (trade_id)
        REFERENCES trade (id) ON DELETE CASCADE,
    CONSTRAINT trade_fill_quantity_positive CHECK (quantity > 0)
);

-- 조회는 대부분 "닫힌 거래를 청산 시각 구간으로" 다. 부분 인덱스로 계획·미청산을 제외한다.
CREATE INDEX trade_closed_exit_at_idx ON trade (exit_at) WHERE state = 'CLOSED';

COMMENT ON TABLE trade IS '매매 기록. 계획 → 체결 → 청산이 한 행의 상태 전이다.';
