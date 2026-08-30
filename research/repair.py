"""덤프가 준 1분봉과 거래소가 지금 주는 값을 맞대고, 어긋난 구간만 다시 받는다.

**왜 필요한가.** 접은 4시간봉을 바이낸스 REST 의 4시간봉과 맞댔더니 13,137봉 중 28봉이
달랐다(0.213%). 원인은 우리 코드가 아니라 **월별 덤프 자체**다 — 실제로 거래가 있었던 분이
덤프에서는 시가=고가=저가=종가, 거래량 0 인 평평한 봉으로 들어 있다. 그런 분이 369개다.

**어느 쪽을 믿는가.** REST 다. 이 저장소의 지표는 트레이딩뷰·바이낸스 차트와 대조해 정의를
확정해 왔고, 그 화면들이 보여 주는 것이 REST 계열의 값이다. 덤프만 믿으면 **차트에 없는
평평한 봉 위에서 지표를 계산하게 된다.**

**전부 다시 받지 않는다.** 어긋난 4시간봉의 분들만 받는다 — 315만 분을 다시 받는 것은
IP 차단을 부르고, 맞는 값을 덮어써서 얻을 것도 없다.

**고친 뒤 다시 잰다.** 고쳤다고 말하지 않고 같은 대조를 한 번 더 돌려 숫자를 보여 준다.

**거래량은 끝까지 남는다.** 고친 뒤에도 12봉의 거래량이 0.001~0.61% 다른데, 그것은 거래소가
자기 1분봉과 4시간봉을 다르게 집계하는 자리다 — REST 에서 1분봉을 새로 받아 접어도 REST 의
4시간봉과 맞지 않는다. **우리가 고칠 수 있는 것이 아니므로 가격과 갈라서 센다.** 지표는
전부 시가·고가·저가·종가에서 나오므로 이 차이는 § 4 의 어느 칸에도 들어가지 않는다.

    python repair.py --report   # 어긋난 봉이 몇 개인지만 본다
    python repair.py            # 어긋난 구간을 다시 받아 덮어쓴다
"""

import sys
import time

import bars
import config
import rest
import store

#: 접은 봉과 거래소 봉이 이만큼 넘게 다르면 어긋난 것으로 본다. 부동소수 오차만 흡수한다.
PRICE_TOLERANCE = 1e-6

VOLUME_TOLERANCE = 1e-3

#: 대조에 쓰는 주기. **4시간인 이유는 한 번에 6년이 아홉 번 호출로 끝나기 때문이다** —
#: 어긋난 봉이 있으면 그 안의 분들을 다시 받으므로 더 잘게 볼 이유가 없다.
INTERVAL = "4h"

WIDTH_MS = bars.INTERVALS[INTERVAL] * bars.MINUTE_MS


def exchange_bars(start, end):
    """거래소가 지금 주는 4시간봉. `{open_time: (o, h, l, c, v)}`."""
    out = {}
    cursor = start
    while cursor <= end:
        rows = rest.klines("perp", INTERVAL, cursor, end)
        if not rows:
            break
        for row in rows:
            out[int(row[0])] = tuple(float(row[i]) for i in (1, 2, 3, 4, 5))
        cursor = int(rows[-1][0]) + 1
        time.sleep(0.3)
    return out


def mismatched(conn, prices_only=False):
    """어긋난 4시간봉의 시작 시각. `prices_only` 면 거래량 차이는 세지 않는다."""
    folded = {bar["start"]: (bar["open"], bar["high"], bar["low"], bar["close"], bar["volume"])
              for bar in bars.fold(conn, bars.INTERVALS[INTERVAL])}
    if not folded:
        return []
    theirs = exchange_bars(min(folded), max(folded))
    return [start for start, ours in sorted(folded.items())
            if start in theirs and _differs(ours, theirs[start], prices_only)]


def _differs(ours, theirs, prices_only=False):
    prices = any(abs(a - b) > PRICE_TOLERANCE for a, b in zip(ours[:4], theirs[:4]))
    if prices_only:
        return prices
    return prices or abs(ours[4] - theirs[4]) > VOLUME_TOLERANCE


def refetch(conn, starts):
    """그 구간의 1분봉을 거래소에서 다시 받아 덮어쓴다."""
    minutes = 0
    for start in starts:
        rows = rest.klines("perp", "1m", start, start + WIDTH_MS - 1)
        _overwrite(conn, rows)
        minutes += len(rows)
        time.sleep(0.3)
    conn.commit()
    return minutes


def _overwrite(conn, rows):
    """`store.upsert_klines` 는 충돌을 무시한다 — 여기서는 **덮어쓰는 것이 목적**이다."""
    conn.executemany(
        "INSERT INTO kline (market, interval, open_time, open, high, low, close, volume, "
        "quote_volume, trades, taker_buy_volume, close_time) "
        "VALUES (?,?,?,?,?,?,?,?,?,?,?,?) "
        "ON CONFLICT (market, interval, open_time) DO UPDATE SET "
        "open=excluded.open, high=excluded.high, low=excluded.low, close=excluded.close, "
        "volume=excluded.volume, quote_volume=excluded.quote_volume, trades=excluded.trades, "
        "taker_buy_volume=excluded.taker_buy_volume, close_time=excluded.close_time",
        [("perp", "1m", int(r[0]), float(r[1]), float(r[2]), float(r[3]), float(r[4]),
          float(r[5]), float(r[7]), int(float(r[8])), float(r[9]), int(r[6])) for r in rows],
    )


def main():
    report_only = "--report" in sys.argv
    with store.connect(config.DB_PATH) as conn:
        before = mismatched(conn)
        print(f"어긋난 4시간봉 {len(before)}개")
        if report_only or not before:
            return
        minutes = refetch(conn, before)
        print(f"{minutes:,}분을 다시 받아 덮어썼다")
        after = mismatched(conn)
        prices = mismatched(conn, prices_only=True)
        print(f"고친 뒤 어긋난 4시간봉 {len(after)}개 (그중 가격이 다른 것 {len(prices)}개)")
        for start in prices[:10]:
            print(f"  가격 남음 {start}")


if __name__ == "__main__":
    main()
