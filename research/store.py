"""SQLite 저장소.

**스냅샷 한 행은 한 시각의 사실 묶음이다.** 그래서 지표마다 값과 `*_ts` 를 쌍으로 갖는다 —
`collected_at` 은 우리가 이 행을 쓴 시각이고 `*_ts` 는 거래소가 그 값에 붙인 시각이다.
둘을 뭉개면 나중에 룩어헤드 여부를 검증할 수 없다.

**미래를 가리키는 행은 쓸 수 없다.** `insert_snapshot` 이 모든 `*_ts` 가 슬롯 시각 이하인지
검사하고 아니면 던진다. 룩어헤드는 분석 단계에서 잡기 가장 어려운 버그이고, 잡을 수 있는
가장 이른 자리가 저장이다.
"""

import sqlite3
from contextlib import contextmanager

from config import DB_PATH, SLOT_MS

#: 스냅샷의 지표 칸. 각각 `{이름}` 과 `{이름}_ts` 를 갖는다.
#: 순서가 곧 스키마 순서이고, 늘리면 마이그레이션이 필요하다.
METRIC_COLUMNS = [
    "perp_price",
    "spot_price",
    "funding_rate",
    "open_interest",
    "open_interest_value",
    "long_short_account_ratio",
    "top_trader_account_ratio",
    "top_trader_position_ratio",
    "taker_buy_sell_ratio",
    "book_spread",
    "book_bid_depth",
    "book_ask_depth",
    "book_imbalance",
]

#: 값에서 곧바로 나오는 것들. 저장하는 이유는 재계산 비용이 아니라 **정의를 한 곳에 두기
#: 위해서다** — 베이시스를 분석마다 다시 정의하면 그 정의가 갈라진다.
DERIVED_COLUMNS = ["basis", "basis_rate"]

_SCHEMA = f"""
CREATE TABLE IF NOT EXISTS snapshot (
    slot_ts      INTEGER PRIMARY KEY,
    collected_at INTEGER NOT NULL,
    source       TEXT    NOT NULL,
    {", ".join(f"{c} REAL, {c}_ts INTEGER" for c in METRIC_COLUMNS)},
    {", ".join(f"{c} REAL" for c in DERIVED_COLUMNS)}
);

CREATE TABLE IF NOT EXISTS kline (
    market      TEXT    NOT NULL,
    interval    TEXT    NOT NULL,
    open_time   INTEGER NOT NULL,
    open        REAL    NOT NULL,
    high        REAL    NOT NULL,
    low         REAL    NOT NULL,
    close       REAL    NOT NULL,
    volume      REAL    NOT NULL,
    quote_volume REAL,
    trades      INTEGER,
    taker_buy_volume REAL,
    close_time  INTEGER NOT NULL,
    PRIMARY KEY (market, interval, open_time)
);

-- 5분 격자 원시 파생 통계. 15분 스냅샷은 이 위에서 조립한다.
-- **버리지 않는 이유:** 슬롯 폭을 바꾸고 싶어지면 다시 받아야 하고, 그때 덤프가 그대로
-- 있으리라는 보장이 없다. 받은 것은 받은 대로 남긴다.
CREATE TABLE IF NOT EXISTS metric_5m (
    ts                        INTEGER PRIMARY KEY,
    open_interest             REAL,
    open_interest_value       REAL,
    long_short_account_ratio  REAL,
    top_trader_account_ratio  REAL,
    top_trader_position_ratio REAL,
    taker_buy_sell_ratio      REAL
);

CREATE TABLE IF NOT EXISTS funding (
    funding_time INTEGER PRIMARY KEY,
    funding_rate REAL NOT NULL
);

-- 무엇을 언제 어디서 받았는지. 백필이 중간에 죽어도 이어서 돌 수 있는 근거다.
CREATE TABLE IF NOT EXISTS ingest_log (
    kind      TEXT    NOT NULL,
    key       TEXT    NOT NULL,
    rows      INTEGER NOT NULL,
    done_at   INTEGER NOT NULL,
    PRIMARY KEY (kind, key)
);

CREATE INDEX IF NOT EXISTS kline_time ON kline (market, interval, close_time);
CREATE INDEX IF NOT EXISTS snapshot_book ON snapshot (slot_ts) WHERE book_spread IS NOT NULL;
"""


class LookaheadError(ValueError):
    """슬롯보다 미래의 시각을 가진 값을 쓰려 했다."""


@contextmanager
def connect(path=DB_PATH):
    path.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(path, timeout=60)
    conn.execute("PRAGMA journal_mode=WAL")
    conn.execute("PRAGMA synchronous=NORMAL")
    try:
        yield conn
        conn.commit()
    finally:
        conn.close()


def init(conn):
    conn.executescript(_SCHEMA)


def check_slot(slot_ts):
    if slot_ts % SLOT_MS != 0:
        raise ValueError(f"슬롯이 {SLOT_MS}ms 격자에 맞지 않는다: {slot_ts}")
    return slot_ts


def upsert_snapshot(conn, slot_ts, collected_at, source, values):
    """스냅샷 한 행을 쓴다. `values` 는 `{지표: (값, source_ts)}`.

    빠진 지표는 NULL 로 남는다 — **채우지 않는다.** forward-fill 은 분석 단계에서 명시적으로
    고르는 것이지 저장이 몰래 해 줄 일이 아니다.
    """
    check_slot(slot_ts)
    cols, params = ["slot_ts", "collected_at", "source"], [slot_ts, collected_at, source]
    for name, pair in values.items():
        if name not in METRIC_COLUMNS:
            raise KeyError(f"모르는 지표: {name}")
        value, ts = pair
        if value is None:
            continue
        if ts is not None and ts > slot_ts:
            raise LookaheadError(
                f"{name} 의 출처 시각이 슬롯보다 미래다: {ts} > {slot_ts}"
            )
        cols += [name, f"{name}_ts"]
        params += [float(value), ts]

    perp = values.get("perp_price", (None, None))[0]
    spot = values.get("spot_price", (None, None))[0]
    if perp is not None and spot is not None and float(spot) != 0:
        cols += ["basis", "basis_rate"]
        params += [float(perp) - float(spot), (float(perp) - float(spot)) / float(spot)]

    updates = ", ".join(f"{c}=excluded.{c}" for c in cols if c != "slot_ts")
    conn.execute(
        f"INSERT INTO snapshot ({', '.join(cols)}) VALUES ({', '.join('?' * len(cols))}) "
        f"ON CONFLICT(slot_ts) DO UPDATE SET {updates}",
        params,
    )


def upsert_klines(conn, market, interval, rows):
    """캔들을 넣는다. `rows` 는 바이낸스 배열 순서 그대로."""
    conn.executemany(
        "INSERT INTO kline (market, interval, open_time, open, high, low, close, volume, "
        "quote_volume, trades, taker_buy_volume, close_time) "
        "VALUES (?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT DO NOTHING",
        [
            (
                market, interval, int(r[0]),
                float(r[1]), float(r[2]), float(r[3]), float(r[4]), float(r[5]),
                float(r[7]) if len(r) > 7 else None,
                int(float(r[8])) if len(r) > 8 else None,
                float(r[9]) if len(r) > 9 else None,
                int(r[6]),
            )
            for r in rows
        ],
    )


#: `metric_5m` 의 지표 칸. 순서가 곧 `upsert_metrics` 의 인자 순서다.
METRIC_5M_COLUMNS = [
    "open_interest",
    "open_interest_value",
    "long_short_account_ratio",
    "top_trader_account_ratio",
    "top_trader_position_ratio",
    "taker_buy_sell_ratio",
]


def upsert_metrics(conn, rows):
    """`(ts, {지표: 값})` 목록을 넣는다. 없는 지표는 NULL 로 남는다."""
    cols = ", ".join(METRIC_5M_COLUMNS)
    marks = ", ".join("?" * len(METRIC_5M_COLUMNS))
    conn.executemany(
        f"INSERT INTO metric_5m (ts, {cols}) VALUES (?, {marks}) "
        f"ON CONFLICT(ts) DO UPDATE SET "
        + ", ".join(f"{c}=COALESCE(excluded.{c}, {c})" for c in METRIC_5M_COLUMNS),
        [(ts, *[values.get(c) for c in METRIC_5M_COLUMNS]) for ts, values in rows],
    )


def assert_no_lookahead(conn):
    """어떤 행도 슬롯보다 미래의 출처 시각을 갖지 않는지 본다.

    **조립을 SQL 로 하기 때문에 필요하다.** 행마다 파이썬을 거치면 20만 행이 느리고, 그렇다고
    검사를 빼면 룩어헤드가 조용히 들어온다 — 이 파이프라인에서 가장 치명적인 버그다.
    조립 뒤에 한 번 전수로 묻는 것이 그 절충이다.
    """
    checks = " OR ".join(f"{c}_ts > slot_ts" for c in METRIC_COLUMNS)
    bad = conn.execute(f"SELECT COUNT(*) FROM snapshot WHERE {checks}").fetchone()[0]
    if bad:
        raise LookaheadError(f"출처 시각이 슬롯보다 미래인 행이 {bad}개 있다")
    return True


def upsert_funding(conn, rows):
    conn.executemany(
        "INSERT INTO funding (funding_time, funding_rate) VALUES (?,?) ON CONFLICT DO NOTHING",
        rows,
    )


def mark_done(conn, kind, key, rows, now_ms):
    conn.execute(
        "INSERT INTO ingest_log (kind, key, rows, done_at) VALUES (?,?,?,?) "
        "ON CONFLICT(kind, key) DO UPDATE SET rows=excluded.rows, done_at=excluded.done_at",
        (kind, key, rows, now_ms),
    )


def done_keys(conn, kind):
    return {r[0] for r in conn.execute("SELECT key FROM ingest_log WHERE kind=?", (kind,))}
