"""덤프로 과거를 채운다. **중간에 죽어도 이어서 돈다.**

`ingest_log` 에 "무엇을 받았는가" 를 파일 단위로 적어 두고, 다시 돌리면 안 받은 것만 받는다.
파일 하나가 실패해도 나머지는 계속 간다 — 한 달치가 없다고 6년치를 다시 받는 것이 최악이다.

**REST 는 덤프가 아직 없는 자리에만 쓴다.** 덤프는 T-1 까지 올라오므로 오늘 몫만 REST 다.
파생 통계는 REST 로도 31일까지만 오므로 이 경계가 무너지면 그날부터 구멍이 난다 —
`report()` 가 그 구멍을 지표별로 센다.

    python backfill.py            # 전부
    python backfill.py --report   # 받지 않고 현재 상태만
"""

import argparse
import datetime as dt
import sys
import time

# 윈도우 콘솔이 cp949 라 한글 밖의 기호에서 죽는다. 출력만 UTF-8 로 고정한다.
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")

import dumps
import rest
import snapshots
import store
from config import BACKFILL_START, DUMP_LAG_DAYS, SLOT_MS

UTC = dt.timezone.utc


def _ms(d):
    return int(dt.datetime(d.year, d.month, d.day, tzinfo=UTC).timestamp() * 1000)


def _months(first, last):
    y, m = first.year, first.month
    while (y, m) <= (last.year, last.month):
        yield f"{y:04d}-{m:02d}"
        y, m = (y + 1, 1) if m == 12 else (y, m + 1)


def _days(first, last):
    d = first
    while d <= last:
        yield d
        d += dt.timedelta(days=1)


def _log(msg):
    print(f"[{dt.datetime.now(UTC):%H:%M:%S}] {msg}", flush=True)


def backfill_klines(conn, market, interval, start_day, last_dump_day):
    kind = f"kline:{market}:{interval}"
    done = store.done_keys(conn, kind)
    current_month = (last_dump_day.year, last_dump_day.month)

    for month in _months(start_day, last_dump_day):
        y, m = int(month[:4]), int(month[5:])
        if (y, m) == current_month or month in done:
            continue
        try:
            rows = dumps.klines(market, interval, month)
        except dumps.DumpMissing:
            _log(f"  {kind} {month} 덤프 없음 — 건너뜀")
            continue
        store.upsert_klines(conn, market, interval, rows)
        store.mark_done(conn, kind, month, len(rows), int(time.time() * 1000))
        conn.commit()
        _log(f"  {kind} {month}: {len(rows)}행")

    # 이번 달은 일별 덤프로. 월별 파일은 달이 끝나야 올라온다.
    month_first = dt.date(last_dump_day.year, last_dump_day.month, 1)
    for day in _days(max(month_first, start_day), last_dump_day):
        key = day.isoformat()
        if key in done:
            continue
        try:
            rows = dumps.klines(market, interval, key, daily=True)
        except dumps.DumpMissing:
            _log(f"  {kind} {key} 일별 덤프 없음 — 건너뜀")
            continue
        store.upsert_klines(conn, market, interval, rows)
        store.mark_done(conn, kind, key, len(rows), int(time.time() * 1000))
        conn.commit()
        _log(f"  {kind} {key}: {len(rows)}행")

    # 덤프가 아직 없는 오늘 몫은 REST 로.
    tail_start = _ms(last_dump_day + dt.timedelta(days=1))
    now = int(time.time() * 1000)
    if tail_start < now:
        rows = rest.klines(market, interval, tail_start, now)
        store.upsert_klines(conn, market, interval, rows)
        conn.commit()
        _log(f"  {kind} REST 꼬리: {len(rows)}행")


def backfill_metrics(conn, start_day, last_dump_day):
    done = store.done_keys(conn, "metrics")
    pending = [d for d in _days(start_day, last_dump_day) if d.isoformat() not in done]
    _log(f"파생 통계: 남은 {len(pending)}일")
    for i, day in enumerate(pending, 1):
        key = day.isoformat()
        try:
            rows = dumps.metrics_day(key)
        except dumps.DumpMissing:
            _log(f"  metrics {key} 없음 — 건너뜀")
            continue
        store.upsert_metrics(conn, rows)
        store.mark_done(conn, "metrics", key, len(rows), int(time.time() * 1000))
        if i % 50 == 0:
            conn.commit()
            _log(f"  metrics {key} ({i}/{len(pending)})")
    conn.commit()

    # 덤프가 없는 오늘 몫. 31일 안이므로 REST 가 답한다.
    recent = rest.metrics_5m(limit=500)
    store.upsert_metrics(conn, sorted(recent.items()))
    conn.commit()
    _log(f"  metrics REST 꼬리: {len(recent)}격자")


def backfill_funding(conn, start_day, last_dump_day):
    """펀딩비는 REST 로만 받는다.

    **덤프를 쓰지 않는 이유는 간격이 메워지지 않기 때문이다.** 월별 덤프는 달이 끝나야
    올라오므로 이번 달이 통째로 비고, 그 구멍을 "저장된 마지막 시각부터" 로 메우려 하면
    구멍보다 뒤에서 시작해 영영 안 메워진다. 8시간에 한 건이라 6년치가 7,000건도 안 되고
    요청 몇 번이면 끝난다 — 여기서는 단순한 쪽이 옳다.
    """
    rows = rest.funding(_ms(start_day), int(time.time() * 1000))
    store.upsert_funding(conn, rows)
    conn.commit()
    _log(f"펀딩비: REST {len(rows)}건")


def report(conn):
    total, counts = snapshots.coverage(conn)
    print()
    print(f"스냅샷 행수: {total:,}")
    if not total:
        return
    span = conn.execute("SELECT MIN(slot_ts), MAX(slot_ts) FROM snapshot").fetchone()
    fmt = lambda ms: dt.datetime.fromtimestamp(ms / 1000, UTC).strftime("%Y-%m-%d %H:%M")
    print(f"기간: {fmt(span[0])} ~ {fmt(span[1])} (UTC)")
    print()
    print(f"{'지표':28s} {'채워진 행':>10s} {'비율':>8s}")
    for name, n in counts.items():
        print(f"{name:28s} {n:10,d} {n / total * 100:7.1f}%")


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--report", action="store_true", help="받지 않고 상태만 본다")
    ap.add_argument("--from", dest="start", default=BACKFILL_START)
    args = ap.parse_args(argv)

    start_day = dt.date.fromisoformat(args.start)
    last_dump_day = dt.datetime.now(UTC).date() - dt.timedelta(days=DUMP_LAG_DAYS)

    with store.connect() as conn:
        store.init(conn)
        if args.report:
            report(conn)
            return 0

        _log(f"백필 {start_day} ~ {last_dump_day} (덤프) + 오늘 (REST)")
        backfill_funding(conn, start_day, last_dump_day)
        backfill_metrics(conn, start_day, last_dump_day)
        for market in ("perp", "spot"):
            _log(f"{market} 1분봉")
            backfill_klines(conn, market, "1m", start_day, last_dump_day)

        _log("스냅샷 조립")
        end = (int(time.time() * 1000) // SLOT_MS) * SLOT_MS
        n = snapshots.build(conn, _ms(start_day), end, source="backfill")
        _log(f"스냅샷 {n:,}행 — 룩어헤드 검사 통과")
        report(conn)
    return 0


if __name__ == "__main__":
    sys.exit(main())
