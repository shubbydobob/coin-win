"""라벨 — 각 슬롯 뒤에 실제로 무슨 일이 있었나.

Phase 2. 스냅샷이 "그 시각에 알려져 있던 사실" 이라면 라벨은 **그 뒤에 일어난 일**이다.
다른 표에 두는 이유가 그것이다 — 한 행에 섞으면 어느 칸이 미래를 보고 있는지가 칼럼 이름에만
남고, 이 파이프라인에서 가장 치명적인 버그를 이름에 기대어 막는 꼴이 된다.

**기준가는 스냅샷의 `perp_price` 다.** 창의 첫 봉 시가를 쓰면 라벨이 그 시점에 알 수 없던 값
위에서 계산된다 — 특징과 라벨이 서로 다른 가격을 기준으로 삼으면 "이 신호 뒤에 얼마나
올랐나" 라는 질문 자체가 흐려진다.

**창은 `[슬롯, 슬롯 + h시간)` 이다.** 스냅샷의 가격은 슬롯 직전에 닫힌 1분봉에서 오므로
(`snapshots.py` 의 룩어헤드 규칙 1) 기준가를 만든 봉과 창은 겹치지 않는다. 겹치면 "그 봉의
고가" 가 기준가에도 라벨에도 들어가 상관이 부풀어 오른다.

**MFE·MAE 는 롱 기준 한 벌만 저장한다.** 숏은 부호를 뒤집은 거울이고(`short_view`), 두 벌을
저장하면 같은 정의가 두 곳에 생긴다.

**창이 한 봉이라도 비면 라벨을 만들지 않는다.** 최대·최소는 결측에 비대칭으로 반응한다 —
빠진 봉이 극값이었으면 MFE·MAE 가 실제보다 **작게** 나오고, 그 방향의 오차는 "덜 위험해
보이는" 쪽이다. 결측을 채우지 않는다는 규칙이 여기서는 안전 쪽으로 작동한다.

**끝나지 않은 창도 만들지 않는다.** 24시간 라벨은 마지막 하루가 비고, 그것이 정상이다.
반쯤 찬 창으로 라벨을 내면 최근 구간만 체계적으로 덜 극단적인 값을 갖게 된다.

    python labels.py            # 없는 것만 계산한다
    python labels.py --report   # 계산하지 않고 채움률만 본다
    python labels.py --rebuild  # 전부 다시 만든다
"""

import sys
import time
from array import array
from collections import deque

import store
from config import HORIZONS_HOURS

MINUTE_MS = 60_000

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")


def load_minutes(conn, market="perp"):
    """1분봉을 분 단위 격자에 편다.

    빠진 분은 `present` 가 0 이다. **채우지 않는다** — 창의 완결성을 세는 것이 이 배열의
    유일한 일이고, 메우면 그 사실이 사라진다.
    """
    first, last = conn.execute(
        "SELECT MIN(open_time), MAX(open_time) FROM kline WHERE market=? AND interval='1m'",
        (market,),
    ).fetchone()
    if first is None:
        return 0, array("d"), array("d"), array("d"), bytearray()

    n = (last - first) // MINUTE_MS + 1
    highs, lows, closes = (array("d", [0.0]) * n for _ in range(3))
    present = bytearray(n)
    rows = conn.execute(
        "SELECT open_time, high, low, close FROM kline "
        "WHERE market=? AND interval='1m' ORDER BY open_time",
        (market,),
    )
    for open_time, high, low, close in rows:
        i = (open_time - first) // MINUTE_MS
        highs[i], lows[i], closes[i], present[i] = high, low, close, 1
    return first, highs, lows, closes, present


def _prefix_present(present):
    """`pref[i]` = `[0, i)` 안의 실제로 있는 봉 수. 창의 완결성을 O(1) 로 묻기 위해서다."""
    pref = array("i", [0]) * (len(present) + 1)
    total = 0
    for i, p in enumerate(present):
        total += p
        pref[i + 1] = total
    return pref


def windows(slots, first_ms, highs, lows, present, pref, minutes):
    """`(슬롯, 최고가, 최저가)` 를 슬롯 순서대로 낸다. 창이 덜 찼으면 건너뛴다.

    창의 시작과 끝이 함께 앞으로만 가므로 단조 덱으로 O(n) 에 끝난다. 슬롯마다 다시 훑으면
    24시간 창에서 21만 × 1,440 이 되고, 그것이 이 함수가 존재하는 이유다.
    """
    n = len(present)
    dq_hi, dq_lo, cursor = deque(), deque(), 0
    for slot in slots:
        left = (slot - first_ms) // MINUTE_MS
        right = left + minutes
        if left < 0 or right > n:
            continue
        if pref[right] - pref[left] != minutes:
            continue
        cursor = max(cursor, left)
        while cursor < right:
            if present[cursor]:
                while dq_hi and highs[dq_hi[-1]] <= highs[cursor]:
                    dq_hi.pop()
                dq_hi.append(cursor)
                while dq_lo and lows[dq_lo[-1]] >= lows[cursor]:
                    dq_lo.pop()
                dq_lo.append(cursor)
            cursor += 1
        while dq_hi and dq_hi[0] < left:
            dq_hi.popleft()
        while dq_lo and dq_lo[0] < left:
            dq_lo.popleft()
        yield slot, highs[dq_hi[0]], lows[dq_lo[0]], right - 1


def short_view(ret, mfe, mae):
    """롱 기준 라벨을 숏 기준으로 뒤집는다. 저장하지 않고 읽는 쪽에서 부른다."""
    return -ret, -mae, -mfe


def build(conn, horizons=HORIZONS_HOURS, rebuild=False, market="perp"):
    """없는 라벨을 채운다. `(호라이즌, 새로 쓴 행 수)` 목록을 돌려준다."""
    store.init(conn)
    if rebuild:
        conn.execute("DELETE FROM label")

    first_ms, highs, lows, closes, present = load_minutes(conn, market)
    if not present:
        return [(h, 0) for h in horizons]
    pref = _prefix_present(present)

    bases = conn.execute(
        "SELECT slot_ts, perp_price FROM snapshot WHERE perp_price IS NOT NULL ORDER BY slot_ts"
    ).fetchall()
    base_of = dict(bases)
    now_ms = int(time.time() * 1000)

    written = []
    for hours in horizons:
        done = {
            r[0]
            for r in conn.execute("SELECT slot_ts FROM label WHERE horizon_h=?", (hours,))
        }
        slots = [s for s, _ in bases if s not in done]
        rows = []
        for slot, high, low, last in windows(
            slots, first_ms, highs, lows, present, pref, hours * 60
        ):
            base = base_of[slot]
            if base <= 0:
                continue
            rows.append(
                (
                    slot,
                    hours,
                    base,
                    closes[last] / base - 1.0,
                    high / base - 1.0,
                    low / base - 1.0,
                    now_ms,
                )
            )
        conn.executemany(
            "INSERT INTO label (slot_ts, horizon_h, base_price, ret, mfe, mae, computed_at) "
            "VALUES (?,?,?,?,?,?,?) ON CONFLICT(slot_ts, horizon_h) DO UPDATE SET "
            "base_price=excluded.base_price, ret=excluded.ret, mfe=excluded.mfe, "
            "mae=excluded.mae, computed_at=excluded.computed_at",
            rows,
        )
        conn.commit()
        written.append((hours, len(rows)))
        print(f"  {hours:>2}시간 라벨 {len(rows):,}행")
    return written


def assert_ordered(conn):
    """어떤 라벨도 `MAE ≤ 수익률 ≤ MFE` 를 어기지 않는지 본다.

    고가는 종가 이상이고 저가는 종가 이하이므로 이 부등식은 정의상 참이다. 그래서 **깨지면
    창이 어긋난 것이다** — 인덱스가 한 칸 밀리거나 덱이 지난 창의 극값을 물고 있으면 여기서
    드러난다. 전수로 묻는 이유는 `store.assert_no_lookahead` 와 같다.
    """
    bad = conn.execute(
        "SELECT COUNT(*) FROM label WHERE mae > ret + 1e-12 OR ret > mfe + 1e-12"
    ).fetchone()[0]
    if bad:
        raise ValueError(f"MAE ≤ 수익률 ≤ MFE 를 어긴 라벨이 {bad}개 있다")
    return True


def report(conn):
    total = conn.execute(
        "SELECT COUNT(*) FROM snapshot WHERE perp_price IS NOT NULL"
    ).fetchone()[0]
    print(f"기준가가 있는 슬롯 {total:,}개")
    for hours in HORIZONS_HOURS:
        row = conn.execute(
            "SELECT COUNT(*), AVG(ret), AVG(mfe), AVG(mae), "
            "SUM(CASE WHEN ret > 0 THEN 1 ELSE 0 END) FROM label WHERE horizon_h=?",
            (hours,),
        ).fetchone()
        n, avg_ret, avg_mfe, avg_mae, up = row
        if not n:
            print(f"  {hours:>2}시간  라벨 없음")
            continue
        share = f"{n / total * 100:.1f}%" if total else "-"
        print(
            f"  {hours:>2}시간  {n:,}행 ({share})  "
            f"평균 수익률 {avg_ret * 100:+.4f}%  "
            f"평균 MFE {avg_mfe * 100:+.3f}%  평균 MAE {avg_mae * 100:+.3f}%  "
            f"오른 비율 {up / n * 100:.2f}%"
        )


def main(argv):
    with store.connect() as conn:
        store.init(conn)
        if "--report" in argv:
            report(conn)
            return 0
        started = time.time()
        build(conn, rebuild="--rebuild" in argv)
        assert_ordered(conn)
        print(f"{time.time() - started:.1f}초")
        report(conn)
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
