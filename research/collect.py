"""15분마다 스냅샷을 남긴다. **죽었다 살아나면 공백을 메운다.**

    python collect.py           # 계속 돈다
    python collect.py --once    # 한 슬롯만 (확인용)

**호가는 슬롯 경계 직전에 읽는다.** 경계 뒤에 읽은 호가는 그 슬롯에 대해 미래의 사실이고,
그것을 그 행에 적으면 룩어헤드다 — 저장소의 검사가 실제로 그것을 거절한다. 그래서 경계 몇 초
전에 읽고 **읽은 시각 이상인 첫 경계**에 붙인다. 5초 낡은 호가와 어긋난 인과 중에 전자가 낫다.

**호가는 되만들 수 없다.** 프로세스가 꺼져 있던 동안의 호가는 영영 없다 — 그 자리는 NULL 로
남고 채우지 않는다. 나머지(가격·펀딩비·파생 통계)는 REST 로 메워지므로, 공백 메우기가
살리는 것과 못 살리는 것이 무엇인지가 이 파일에서 갈린다.

**파생 통계의 공백 메우기는 31일까지만 된다.** 그보다 오래 꺼져 있었으면 `backfill.py` 를
돌려야 한다 — 그쪽은 덤프를 보므로 한계가 없다. 이 스크립트는 그 경우 그냥 말한다.
"""

import argparse
import datetime as dt
import math
import sys
import time

import rest
import snapshots
import store
from config import SLOT_MS

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")

UTC = dt.timezone.utc

#: 경계 몇 초 전에 호가를 읽는가.
LEAD_S = 5

#: 5분 격자 하나의 길이.
GRID_MS = 300_000

#: 파생 통계 REST 의 소급 한계. 이보다 오래 꺼져 있었으면 덤프가 필요하다.
METRIC_REST_LIMIT_DAYS = 31


def _now_ms():
    return int(time.time() * 1000)


def _fmt(ms):
    return dt.datetime.fromtimestamp(ms / 1000, UTC).strftime("%Y-%m-%d %H:%M:%S")


def _log(msg):
    print(f"[{_fmt(_now_ms())}] {msg}", flush=True)


def last_complete_slot(conn):
    """가격이 채워진 마지막 슬롯. 여기부터 뒤가 공백이다."""
    return conn.execute(
        "SELECT MAX(slot_ts) FROM snapshot WHERE perp_price IS NOT NULL"
    ).fetchone()[0]


def fill_gap(conn):
    """마지막으로 채워진 슬롯 이후를 REST 로 메우고 스냅샷을 다시 조립한다."""
    now = _now_ms()
    end = (now // SLOT_MS) * SLOT_MS
    last = last_complete_slot(conn)
    if last is None:
        _log("스냅샷이 비어 있다. 먼저 `python backfill.py` 를 돌린다.")
        return 0
    if last >= end:
        return 0

    gap_days = (end - last) / 86_400_000
    _log(f"공백 {_fmt(last)} ~ {_fmt(end)} ({gap_days:.2f}일)")
    if gap_days > METRIC_REST_LIMIT_DAYS:
        _log(f"공백이 {METRIC_REST_LIMIT_DAYS}일을 넘는다. 파생 통계는 REST 로 못 메운다 - "
             f"`python backfill.py` 를 먼저 돌린다.")

    for market in ("perp", "spot"):
        rows = rest.klines(market, "1m", last, now)
        store.upsert_klines(conn, market, "1m", rows)
        _log(f"  {market} 1분봉 {len(rows)}행")

    store.upsert_funding(conn, rest.funding(last, now))

    # 파생 통계는 5분 격자 500개(약 42시간)씩 거슬러 받는다. 한 번에 500 이 최대다.
    cursor, grids = last, 0
    while cursor < now and (now - cursor) / 86_400_000 <= METRIC_REST_LIMIT_DAYS:
        batch = rest.metrics_5m(limit=500, start_ms=cursor,
                                end_ms=min(cursor + 500 * GRID_MS, now))
        if not batch:
            break
        store.upsert_metrics(conn, sorted(batch.items()))
        grids += len(batch)
        cursor = max(batch) + GRID_MS
    _log(f"  파생 통계 {grids}격자")

    conn.commit()
    snapshots.build(conn, (last // SLOT_MS) * SLOT_MS, end + SLOT_MS, source="gapfill")
    conn.commit()
    _log("  스냅샷 다시 조립 - 룩어헤드 검사 통과")
    return 1


def capture_book(conn):
    """지금 호가를 읽어 **읽은 시각 이상인 첫 슬롯**에 적는다."""
    values, ts = rest.order_book()
    if values is None:
        _log("호가가 비었다 - 건너뜀")
        return None
    slot = int(math.ceil(ts / SLOT_MS) * SLOT_MS)
    cols = list(values) + [f"{c}_ts" for c in values]
    params = [values[c] for c in values] + [ts] * len(values)
    # `source` 는 조립된 칸이 어디서 왔는가를 말한다. 호가는 여기서만 오므로 그 칸을
    # 건드리지 않는다 - 덮어쓰면 뒤이은 조립이 다시 덮어 'live' 가 남지 않고, 남더라도
    # **호가가 아니라 나머지 칸의 출처를 잘못 말하게 된다.** 호가의 출처는 `book_*_ts` 다.
    conn.execute(
        f"INSERT INTO snapshot (slot_ts, collected_at, source, {', '.join(cols)}) "
        f"VALUES (?, ?, 'live', {', '.join('?' * len(cols))}) "
        f"ON CONFLICT(slot_ts) DO UPDATE SET collected_at=excluded.collected_at, "
        + ", ".join(f"{c}=excluded.{c}" for c in cols),
        [slot, _now_ms()] + params,
    )
    conn.commit()
    store.assert_no_lookahead(conn)
    _log(f"호가 -> 슬롯 {_fmt(slot)} (관측 {_fmt(ts)}, "
         f"스프레드 {values['book_spread']:.2f}, 불균형 {values['book_imbalance']:+.3f})")
    return slot


def first_boundary(now_ms):
    """지금 이후의 첫 슬롯 경계."""
    return ((now_ms // SLOT_MS) + 1) * SLOT_MS


def advance(boundary, now_ms):
    """이미 지나간 경계를 건너뛴다.

    **이것이 없으면 마지막 5초를 태운다.** 경계 5초 전에 깨어나 일을 마치면 시계는 아직 같은
    슬롯 안이고, 거기서 "다음 경계" 를 다시 계산하면 방금 처리한 그 경계가 또 나온다. 깨어날
    시각이 과거이므로 `sleep(0)` 이 되고, 경계를 넘길 때까지 같은 일을 반복한다 — **실제로
    슬롯마다 호가를 30번씩 받고 있었다.** 로그에는 "다음 수집까지 886초" 만 보여 멀쩡해 보였고,
    슬롯별 기록 횟수를 세고 나서야 드러났다.

    그래서 경계를 매번 계산하지 않고 **들고 간다.** 한 번 처리한 경계로는 다시 돌아오지 않는다.
    한 슬롯보다 오래 걸린 경우에는 밀린 경계를 건너뛴다 — 못 받은 호가는 어차피 못 받는다.
    """
    while boundary - LEAD_S * 1000 <= now_ms:
        boundary += SLOT_MS
    return boundary


def run(once=False):
    with store.connect() as conn:
        store.init(conn)
        _log("시작 - 공백을 먼저 본다")
        fill_gap(conn)
        if once:
            capture_book(conn)
            return 0
        boundary = first_boundary(_now_ms())
        while True:
            delay = max(0.0, (boundary - LEAD_S * 1000 - _now_ms()) / 1000)
            _log(f"다음 수집까지 {delay:.0f}초 (슬롯 {_fmt(boundary)})")
            time.sleep(delay)
            try:
                capture_book(conn)
                fill_gap(conn)
            except Exception as e:                       # noqa: BLE001
                # 한 슬롯이 실패해도 수집기는 살아 있어야 한다. 다음 슬롯이 공백을 메운다.
                _log(f"이번 슬롯 실패: {type(e).__name__}: {e}")
                time.sleep(5)
            boundary = advance(boundary + SLOT_MS, _now_ms())


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--once", action="store_true", help="한 슬롯만 돌고 끝낸다")
    return run(once=ap.parse_args(argv).once)


if __name__ == "__main__":
    sys.exit(main())
