"""1분봉을 판독 주기의 봉으로 접는다.

**왜 파이썬이 접고 자바가 지표를 내는가.** 지표 정의는 자바에만 있어야 한다 — 파이썬에 RSI 를
다시 쓰면 화면이 보여 주는 값과 측정이 판정한 값이 갈라진다(`docs/spec/indicator-usage.md`
§ 5.2). 그런데 6년치 1분봉은 이 SQLite 안에 있고 자바는 그것을 읽지 않는다. 그래서 **접는
일만** 여기서 하고 CSV 로 넘긴다. 접는 것은 정의가 아니라 산술이다.

**완성된 봉만 낸다.** 15분봉이면 1분봉이 정확히 15개 있어야 한다. 모자란 봉을 내면 그 봉의
고가·저가·거래량이 실제보다 작고, 그 값이 지표에 들어가면 **틀린 줄 모르는 채로 그럴듯한
수가 나온다.** 2020-09-01 이후 1분봉에는 구멍이 하나도 없으므로 실제로 빠지는 것은 지금
진행 중인 마지막 봉 하나뿐이다.

**경계는 에폭 기준이다.** 에폭이 UTC 00:00 이고 15·60·240 분이 하루를 나누므로 바이낸스의
봉 경계와 같아진다.

    python bars.py            # 세 주기를 전부 접어 CSV 로 낸다
    python bars.py --report   # 접지 않고 몇 개가 나올지만 본다
"""

import csv
import sys

import config
import store

#: 판독이 보는 주기. **일봉·주봉은 없다** — 15분 격자에서 하루 96 슬롯이 같은 값을 가지므로
#: 표본이 실질적으로 96분의 1 이 된다(`docs/spec/indicator-usage.md` § 4).
INTERVALS = {"15m": 15, "1h": 60, "4h": 240}

MINUTE_MS = 60 * 1000


def path(name):
    return config.DATA_DIR / f"bars-{name}.csv"


def fold(conn, minutes):
    """1분봉을 접는다. **완성된 봉만** 낸다."""
    width = minutes * MINUTE_MS
    rows = conn.execute(
        "SELECT open_time, open, high, low, close, volume FROM kline "
        "WHERE market='perp' AND interval='1m' ORDER BY open_time"
    )

    bars = []
    current = None
    for open_time, o, h, low, c, v in rows:
        start = open_time - (open_time % width)
        if current is None or current["start"] != start:
            if current is not None:
                bars.append(current)
            current = {"start": start, "open": o, "high": h, "low": low,
                       "close": c, "volume": v, "minutes": 1}
            continue
        current["high"] = max(current["high"], h)
        current["low"] = min(current["low"], low)
        current["close"] = c
        current["volume"] += v
        current["minutes"] += 1
    if current is not None:
        bars.append(current)

    return [bar for bar in bars if bar["minutes"] == minutes]


def write(name, bars):
    with open(path(name), "w", newline="", encoding="utf-8") as handle:
        writer = csv.writer(handle)
        writer.writerow(["open_time", "close_time", "open", "high", "low", "close", "volume"])
        width = INTERVALS[name] * MINUTE_MS
        for bar in bars:
            writer.writerow([
                bar["start"], bar["start"] + width - 1,
                bar["open"], bar["high"], bar["low"], bar["close"], bar["volume"],
            ])


def main():
    report_only = "--report" in sys.argv
    with store.connect() as conn:
        for name, minutes in INTERVALS.items():
            bars = fold(conn, minutes)
            first = bars[0]["start"] if bars else None
            last = bars[-1]["start"] if bars else None
            print(f"{name:>4}  완성된 봉 {len(bars):>8,}  {first} ~ {last}")
            if not report_only:
                write(name, bars)
                print(f"      → {path(name)}")


if __name__ == "__main__":
    main()
