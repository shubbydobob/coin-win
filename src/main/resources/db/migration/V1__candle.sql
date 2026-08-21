-- 캔들 저장 테이블.
--
-- Phase 3 완료 조건 "캔들 증분 저장에 중복 없음" 을 보장하는 것은 애플리케이션 코드가 아니라
-- 아래 기본키다. 애플리케이션이 중복 검사를 빠뜨려도 DB 가 거부한다.
--
-- 컬럼명이 candle_interval 인 이유: interval 은 PostgreSQL 예약어다.
CREATE TABLE candle (
    symbol          VARCHAR(20)    NOT NULL,
    candle_interval VARCHAR(5)     NOT NULL,
    open_time       TIMESTAMPTZ    NOT NULL,
    open_price      NUMERIC(20, 2) NOT NULL,
    high_price      NUMERIC(20, 2) NOT NULL,
    low_price       NUMERIC(20, 2) NOT NULL,
    close_price     NUMERIC(20, 2) NOT NULL,
    volume          NUMERIC(30, 8) NOT NULL,

    CONSTRAINT candle_pk PRIMARY KEY (symbol, candle_interval, open_time),

    -- 도메인의 Candle 불변식을 DB 에서도 되풀이한다. 중복이 아니라 이중 방어다.
    -- 어댑터를 거치지 않고 SQL 로 직접 넣는 경로(수동 백필, 마이그레이션)가 언젠가 생긴다.
    CONSTRAINT candle_high_encloses CHECK (high_price >= low_price
                                       AND high_price >= open_price
                                       AND high_price >= close_price),
    CONSTRAINT candle_low_encloses  CHECK (low_price <= open_price
                                       AND low_price <= close_price),
    CONSTRAINT candle_volume_non_negative CHECK (volume >= 0)
);

-- 조회는 항상 (종목, 주기, 시각 구간) 이다. 기본키가 그 순서 그대로라 별도 인덱스가 필요 없다.
COMMENT ON TABLE candle IS '거래소 캔들. 기본키가 증분 저장의 중복을 막는다.';
