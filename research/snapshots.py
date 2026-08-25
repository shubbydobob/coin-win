"""15분 스냅샷을 원시 표에서 조립한다.

**세 규칙이 이 파일의 전부다.**

1. **가격은 슬롯 시각에 이미 닫힌 봉에서 가져온다.** 슬롯 15:00 의 가격은 `open_time`
   14:59 인 1분봉의 종가다 — 그 봉은 14:59:59.999 에 닫혔다. `open_time` 15:00 인 봉을 쓰면
   15:00:59.999 까지의 정보를 15:00 에 아는 것이 되고, **그것이 룩어헤드다.**
2. **파생 통계는 5분 격자에서 시각이 정확히 같은 것만 쓴다.** 15:00 슬롯에 15:05 값을 쓰면
   같은 종류의 누출이고, 15:00 에 없다고 14:55 을 끌어오지도 않는다 — 그것은 forward-fill 이고
   분석 단계에서 명시적으로 고를 일이다.
3. **펀딩비만 마지막 값을 끌어온다.** 8시간마다 한 번 갱신되므로 "슬롯 시각 이하의 마지막
   확정값" 이 그 시점에 실제로 알려진 값이다. 이것은 채워 넣기가 아니라 계단 함수를 읽는 것이다.

**호가 칸은 건드리지 않는다.** 되만들 수 없으므로 수집기가 그때그때 쓴 것이 유일한 사본이다.
"""

import time

from config import SLOT_MS
from store import assert_no_lookahead

#: 조립되는 칸. 호가 넷은 여기 없다 — 일부러다.
_BUILT_COLUMNS = [
    "perp_price", "perp_price_ts",
    "spot_price", "spot_price_ts",
    "funding_rate", "funding_rate_ts",
    "open_interest", "open_interest_ts",
    "open_interest_value", "open_interest_value_ts",
    "long_short_account_ratio", "long_short_account_ratio_ts",
    "top_trader_account_ratio", "top_trader_account_ratio_ts",
    "top_trader_position_ratio", "top_trader_position_ratio_ts",
    "taker_buy_sell_ratio", "taker_buy_sell_ratio_ts",
    "basis", "basis_rate",
]

_SELECT = """
WITH RECURSIVE slots(slot_ts) AS (
    SELECT :start
    UNION ALL SELECT slot_ts + :step FROM slots WHERE slot_ts + :step < :end
)
SELECT
    s.slot_ts,
    :now                                    AS collected_at,
    :source                                 AS source,
    p.close                                 AS perp_price,
    p.close_time                            AS perp_price_ts,
    q.close                                 AS spot_price,
    q.close_time                            AS spot_price_ts,
    (SELECT funding_rate FROM funding
      WHERE funding_time <= s.slot_ts ORDER BY funding_time DESC LIMIT 1),
    (SELECT funding_time FROM funding
      WHERE funding_time <= s.slot_ts ORDER BY funding_time DESC LIMIT 1),
    m.open_interest,             CASE WHEN m.open_interest             IS NULL THEN NULL ELSE m.ts END,
    m.open_interest_value,       CASE WHEN m.open_interest_value       IS NULL THEN NULL ELSE m.ts END,
    m.long_short_account_ratio,  CASE WHEN m.long_short_account_ratio  IS NULL THEN NULL ELSE m.ts END,
    m.top_trader_account_ratio,  CASE WHEN m.top_trader_account_ratio  IS NULL THEN NULL ELSE m.ts END,
    m.top_trader_position_ratio, CASE WHEN m.top_trader_position_ratio IS NULL THEN NULL ELSE m.ts END,
    m.taker_buy_sell_ratio,      CASE WHEN m.taker_buy_sell_ratio      IS NULL THEN NULL ELSE m.ts END,
    CASE WHEN p.close IS NOT NULL AND q.close IS NOT NULL
         THEN p.close - q.close END        AS basis,
    CASE WHEN p.close IS NOT NULL AND q.close IS NOT NULL AND q.close <> 0
         THEN (p.close - q.close) / q.close END AS basis_rate
FROM slots s
LEFT JOIN kline p
       ON p.market = 'perp' AND p.interval = '1m' AND p.open_time = s.slot_ts - 60000
LEFT JOIN kline q
       ON q.market = 'spot' AND q.interval = '1m' AND q.open_time = s.slot_ts - 60000
LEFT JOIN metric_5m m ON m.ts = s.slot_ts
"""


def build(conn, start_ms, end_ms, source="built"):
    """`[start_ms, end_ms)` 의 슬롯을 조립한다. 이미 있는 행은 조립 칸만 덮는다."""
    if start_ms % SLOT_MS or end_ms % SLOT_MS:
        raise ValueError("경계가 15분 격자에 맞지 않는다")
    cols = ["slot_ts", "collected_at", "source"] + _BUILT_COLUMNS
    updates = ", ".join(f"{c}=excluded.{c}" for c in cols if c != "slot_ts")
    conn.execute(
        f"INSERT INTO snapshot ({', '.join(cols)}) {_SELECT} "
        f"ON CONFLICT(slot_ts) DO UPDATE SET {updates}",
        {"start": start_ms, "end": end_ms, "step": SLOT_MS,
         "now": int(time.time() * 1000), "source": source},
    )
    assert_no_lookahead(conn)
    return conn.execute(
        "SELECT COUNT(*) FROM snapshot WHERE slot_ts >= ? AND slot_ts < ?",
        (start_ms, end_ms),
    ).fetchone()[0]


def coverage(conn):
    """지표별로 값이 있는 행이 몇 개인지. 결측을 눈으로 보게 하는 것이 목적이다."""
    from store import METRIC_COLUMNS
    total = conn.execute("SELECT COUNT(*) FROM snapshot").fetchone()[0]
    if not total:
        return total, {}
    parts = ", ".join(f"COUNT({c})" for c in METRIC_COLUMNS)
    counts = conn.execute(f"SELECT {parts} FROM snapshot").fetchone()
    return total, dict(zip(METRIC_COLUMNS, counts))
