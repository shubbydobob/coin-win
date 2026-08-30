"""자바가 찍은 지표 칸을 15분 슬롯 격자에 붙인다.

**여기서 지표를 계산하지 않는다.** 정의는 자바에만 있다(`docs/spec/indicator-usage.md` § 5.2).
이 파일이 하는 일은 **어느 봉을 그 슬롯에 붙일 것인가** 하나뿐이고, 그것이 이 단계에서
가장 틀리기 쉬운 자리다.

**슬롯 시각에 이미 닫힌 봉만 쓴다.** 슬롯 15:00 에서 4시간봉의 마지막 닫힌 봉은 12:00 봉이다
(마감 14:59:59.999). 14:00 에 시작한 봉을 쓰면 그 종가는 15:00 이후에나 확정되고 **그것이
곧 룩어헤드다.** 화면은 아직 닫히지 않은 봉을 쓰고 그것이 실시간에서는 맞지만(§ 5.3),
재는 쪽은 그럴 수 없다.

**저장이 다시 검사한다.** 붙인 뒤 전수로 `bar_close_time < slot_ts` 를 확인하고, 하나라도
어긋나면 던진다 — `store.assert_no_lookahead` 와 같은 태도다.

**파생이다.** 스냅샷과 라벨은 다시 만들 수 없지만 이 표는 CSV 만 있으면 언제든 다시 만들어진다.
그래서 원천 스키마(`store.py`)에 두지 않고 여기서 만든다 — `measurement` 와 같은 자리다.

    python bars.py                          # ① 1분봉을 접는다
    .\\gradlew.bat indicatorDump             # ② 자바가 봉마다 지표를 찍는다
    python indicators.py                    # ③ 슬롯에 붙인다
    python indicators.py --report           # 붙이지 않고 채움률만 본다
"""

import csv
import sys

import bars
import config
import store

#: 붙일 주기. `bars.py` 와 같아야 한다 — 다르면 CSV 가 없어서 조용히 빈다.
INTERVALS = list(bars.INTERVALS)

#: 값이 글자인 칸. 나머지는 수다.
TEXT_COLUMNS = ("cloud_position", "ma_order")

#: 값이 정수인 칸. 분위로 자르지 않고 값 하나가 한 칸이 되는 것들과 봉 수 둘이 섞여 있다.
INT_COLUMNS = ("cloud_bullish", "band_walk", "macd_zero_side", "macd_bars_since_cross")

#: 봉을 가리키는 칸. 지표가 아니라 근거다 — 어느 봉에서 온 값인지 없으면 룩어헤드를 검사할 수 없다.
BAR_COLUMNS = ("bar_open_time", "bar_close_time")


class LookaheadError(ValueError):
    """슬롯보다 늦게 닫힌 봉을 붙이려 했다."""


def path(interval):
    return config.DATA_DIR / f"indicators-{interval}.csv"


def columns(interval):
    """CSV 머리에서 지표 칸 이름을 읽는다. `open_time` · `close_time` 은 뺀다."""
    with open(path(interval), encoding="utf-8") as handle:
        head = next(csv.reader(handle))
    return [name for name in head if name not in ("open_time", "close_time")]


def _sql_type(name):
    if name in TEXT_COLUMNS:
        return "TEXT"
    return "INTEGER" if name in INT_COLUMNS else "REAL"


def schema(interval):
    cols = ", ".join(f"{name} {_sql_type(name)}" for name in columns(interval))
    return (
        f"DROP TABLE IF EXISTS indicator_{_suffix(interval)};\n"
        f"CREATE TABLE indicator_{_suffix(interval)} (\n"
        f"    slot_ts INTEGER PRIMARY KEY,\n"
        f"    bar_open_time INTEGER NOT NULL,\n"
        f"    bar_close_time INTEGER NOT NULL,\n"
        f"    {cols}\n"
        f");"
    )


def _suffix(interval):
    """표 이름에 쓸 꼬리. `15m` 처럼 숫자로 시작하는 이름은 SQL 에서 따옴표가 필요하다."""
    return interval.replace("15m", "m15").replace("1h", "h1").replace("4h", "h4")


def read(interval):
    """CSV 를 시간순으로. `(open_time, close_time, {칸: 값})`."""
    out = []
    with open(path(interval), encoding="utf-8") as handle:
        for row in csv.DictReader(handle):
            values = {name: _value(name, row[name]) for name in columns(interval)}
            out.append((int(row["open_time"]), int(row["close_time"]), values))
    out.sort(key=lambda item: item[0])
    return out


def _value(name, raw):
    if raw == "":
        return None
    if name in TEXT_COLUMNS:
        return raw
    return int(raw) if name in INT_COLUMNS else float(raw)


def attach(conn, interval, slots):
    """슬롯마다 **그 시각에 이미 닫힌 마지막 봉**을 고른다. 둘 다 시간순이라 한 번에 훑는다."""
    rows = read(interval)
    out = []
    cursor = 0
    latest = None
    for slot in slots:
        while cursor < len(rows) and rows[cursor][1] < slot:
            latest = rows[cursor]
            cursor += 1
        if latest is not None:
            out.append((slot, latest))
    return out


def store_rows(conn, interval, attached):
    names = columns(interval)
    cols = ", ".join(("slot_ts", *BAR_COLUMNS, *names))
    marks = ", ".join("?" * (3 + len(names)))
    conn.executescript(schema(interval))
    conn.executemany(
        f"INSERT INTO indicator_{_suffix(interval)} ({cols}) VALUES ({marks})",
        [(slot, bar[0], bar[1], *[bar[2][name] for name in names]) for slot, bar in attached],
    )
    conn.commit()


def assert_no_lookahead(conn, interval):
    """붙인 봉이 슬롯보다 늦게 닫힌 것이 하나도 없어야 한다."""
    bad = conn.execute(
        f"SELECT COUNT(*) FROM indicator_{_suffix(interval)} WHERE bar_close_time >= slot_ts"
    ).fetchone()[0]
    if bad:
        raise LookaheadError(f"{interval}: 슬롯보다 늦게 닫힌 봉 {bad}행")


def build(conn, interval):
    slots = [row[0] for row in conn.execute("SELECT slot_ts FROM snapshot ORDER BY slot_ts")]
    attached = attach(conn, interval, slots)
    store_rows(conn, interval, attached)
    assert_no_lookahead(conn, interval)
    return len(attached), len(slots)


def report(conn, interval):
    table = f"indicator_{_suffix(interval)}"
    try:
        filled = conn.execute(f"SELECT COUNT(*) FROM {table}").fetchone()[0]
    except Exception:  # noqa: BLE001 - 표가 아직 없으면 0 이다
        filled = 0
    slots = conn.execute("SELECT COUNT(*) FROM snapshot").fetchone()[0]
    share = 100.0 * filled / slots if slots else 0.0
    print(f"{interval:>4}  {filled:>8,} / {slots:,} 슬롯  ({share:.1f}%)")


def main():
    report_only = "--report" in sys.argv
    with store.connect(config.DB_PATH) as conn:
        for interval in INTERVALS:
            if not path(interval).exists():
                print(f"{interval:>4}  {path(interval)} 없음 — gradlew indicatorDump 를 먼저 돌린다")
                continue
            if not report_only:
                filled, slots = build(conn, interval)
                print(f"{interval:>4}  {filled:>8,} / {slots:,} 슬롯에 붙였다")
            else:
                report(conn, interval)


if __name__ == "__main__":
    main()
