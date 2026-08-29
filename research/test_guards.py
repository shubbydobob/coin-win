"""게이트가 실제로 발동하는지 본다. **통과하는 것처럼 보이는 검사가 가장 위험하다.**

이 저장소는 규칙을 세울 때 위반 픽스처를 함께 둔다(`archFixture`). 여기도 같은 태도다 —
룩어헤드 검사가 있다는 것과 그것이 실제로 막는다는 것은 다른 사실이다.

    python test_guards.py
"""

import sqlite3
import sys
import tempfile
from pathlib import Path

import dumps
import labels
import snapshots
import store

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

PASSED, FAILED = [], []


def case(name):
    def wrap(fn):
        try:
            fn()
            PASSED.append(name)
        except Exception as e:                            # noqa: BLE001
            FAILED.append((name, f"{type(e).__name__}: {e}"))
        return fn
    return wrap


def _fresh():
    conn = sqlite3.connect(Path(tempfile.mkdtemp()) / "t.sqlite")
    store.init(conn)
    return conn


@case("미래 시각을 가진 값은 저장이 거절한다")
def _():
    conn = _fresh()
    slot = 1_700_000_000_000 // store.SLOT_MS * store.SLOT_MS
    try:
        store.upsert_snapshot(conn, slot, slot, "test",
                              {"perp_price": (100.0, slot + 1)})
    except store.LookaheadError:
        return
    raise AssertionError("거절하지 않았다")


@case("같은 시각과 과거 시각은 통과한다")
def _():
    conn = _fresh()
    slot = 1_700_000_000_000 // store.SLOT_MS * store.SLOT_MS
    store.upsert_snapshot(conn, slot, slot, "test",
                          {"perp_price": (100.0, slot), "spot_price": (99.0, slot - 1)})
    row = conn.execute("SELECT perp_price, spot_price, basis FROM snapshot").fetchone()
    assert row == (100.0, 99.0, 1.0), row


@case("전수 검사가 미래 시각 행을 찾아낸다")
def _():
    conn = _fresh()
    slot = 1_700_000_000_000 // store.SLOT_MS * store.SLOT_MS
    # 검사를 우회해 일부러 심는다. 위반 픽스처의 자리다.
    conn.execute("INSERT INTO snapshot (slot_ts, collected_at, source, perp_price, perp_price_ts) "
                 "VALUES (?,?,?,?,?)", (slot, slot, "test", 100.0, slot + 1000))
    try:
        store.assert_no_lookahead(conn)
    except store.LookaheadError:
        return
    raise AssertionError("전수 검사가 지나쳤다")


@case("격자에 안 맞는 슬롯은 거절한다")
def _():
    conn = _fresh()
    try:
        store.upsert_snapshot(conn, 1_700_000_000_001, 0, "test", {})
    except ValueError:
        return
    raise AssertionError("거절하지 않았다")


@case("마이크로초 시각을 밀리초로 맞춘다")
def _():
    assert dumps.to_millis(1782864000000000) == 1782864000000
    assert dumps.to_millis(1782864000000) == 1782864000000


@case("가격은 슬롯에 이미 닫힌 봉에서 온다")
def _():
    conn = _fresh()
    slot = 1_700_000_000_000 // store.SLOT_MS * store.SLOT_MS
    # 슬롯 직전에 닫히는 봉과 슬롯에 열리는 봉을 둘 다 넣는다.
    before = [slot - 60000, 1, 1, 1, 111.0, 1, slot - 1, 0, 0, 0, 0]
    at = [slot, 1, 1, 1, 999.0, 1, slot + 59999, 0, 0, 0, 0]
    store.upsert_klines(conn, "perp", "1m", [before, at])
    store.upsert_klines(conn, "spot", "1m", [before, at])
    snapshots.build(conn, slot, slot + store.SLOT_MS)
    price, ts = conn.execute(
        "SELECT perp_price, perp_price_ts FROM snapshot WHERE slot_ts=?", (slot,)).fetchone()
    assert price == 111.0, f"슬롯에 열린 봉을 썼다: {price}"
    assert ts == slot - 1, ts


@case("파생 통계는 시각이 정확히 같은 격자만 쓴다")
def _():
    conn = _fresh()
    slot = 1_700_000_000_000 // store.SLOT_MS * store.SLOT_MS
    store.upsert_metrics(conn, [(slot - 300_000, {"open_interest": 7.0}),
                                (slot + 300_000, {"open_interest": 9.0})])
    snapshots.build(conn, slot, slot + store.SLOT_MS)
    oi = conn.execute("SELECT open_interest FROM snapshot WHERE slot_ts=?", (slot,)).fetchone()[0]
    assert oi is None, f"이웃 격자를 끌어왔다: {oi}"


@case("같은 슬롯 경계를 두 번 처리하지 않는다")
def _():
    import collect
    slot = collect.SLOT_MS
    boundary = collect.first_boundary(1_700_000_000_000)
    # 경계 1초 전에 일을 마친 상황. 여기서 경계를 다시 계산하면 방금 그 경계가 또 나온다.
    nxt = collect.advance(boundary + slot, boundary - 1000)
    assert nxt == boundary + slot, f"같은 경계로 되돌아왔다: {nxt}"


@case("한 슬롯보다 오래 걸리면 밀린 경계를 건너뛴다")
def _():
    import collect
    slot = collect.SLOT_MS
    boundary = collect.first_boundary(1_700_000_000_000)
    assert collect.advance(boundary + slot, boundary + 2 * slot) > boundary + 2 * slot



# ── 라벨 (Phase 2) ──────────────────────────────────────────────────────────

def _slot0():
    return 1_700_000_000_000 // store.SLOT_MS * store.SLOT_MS


def _minutes(conn, start_ms, bars):
    """`(고가, 저가, 종가)` 목록을 1분봉으로 넣는다."""
    rows = [
        [start_ms + i * 60_000, c, h, lo, c, 1.0, start_ms + i * 60_000 + 59_999,
         0.0, 1, 0.0]
        for i, (h, lo, c) in enumerate(bars)
    ]
    store.upsert_klines(conn, "perp", "1m", rows)


@case("라벨은 슬롯 이전 봉을 보지 않는다")
def _():
    conn = _fresh()
    slot = _slot0()
    # 슬롯 직전 봉에 창 안의 어떤 값보다 높은 고가를 둔다. 창이 그것을 물면 MFE 가 부풀어 오른다.
    _minutes(conn, slot - 60_000, [(999.0, 100.0, 100.0)] + [(101.0, 99.0, 100.0)] * 60)
    store.upsert_snapshot(conn, slot, slot, "test", {"perp_price": (100.0, slot - 1)})
    labels.build(conn, horizons=(1,))
    mfe = conn.execute("SELECT mfe FROM label WHERE slot_ts=?", (slot,)).fetchone()[0]
    assert abs(mfe - 0.01) < 1e-9, f"슬롯 이전 봉을 창에 넣었다: {mfe}"


@case("창이 덜 찼으면 라벨을 만들지 않는다")
def _():
    conn = _fresh()
    slot = _slot0()
    _minutes(conn, slot, [(101.0, 99.0, 100.0)] * 59)          # 한 봉 모자란다
    store.upsert_snapshot(conn, slot, slot, "test", {"perp_price": (100.0, slot - 1)})
    labels.build(conn, horizons=(1,))
    assert conn.execute("SELECT COUNT(*) FROM label").fetchone()[0] == 0, "덜 찬 창으로 라벨을 냈다"


@case("창 가운데가 비어도 라벨을 만들지 않는다")
def _():
    conn = _fresh()
    slot = _slot0()
    _minutes(conn, slot, [(101.0, 99.0, 100.0)] * 30)
    _minutes(conn, slot + 31 * 60_000, [(101.0, 99.0, 100.0)] * 30)   # 31번째 분이 없다
    store.upsert_snapshot(conn, slot, slot, "test", {"perp_price": (100.0, slot - 1)})
    labels.build(conn, horizons=(1,))
    assert conn.execute("SELECT COUNT(*) FROM label").fetchone()[0] == 0, "구멍 난 창으로 라벨을 냈다"


@case("MAE ≤ 수익률 ≤ MFE 를 어기면 검사가 잡는다")
def _():
    conn = _fresh()
    slot = _slot0()
    conn.execute(
        "INSERT INTO label (slot_ts, horizon_h, base_price, ret, mfe, mae, computed_at) "
        "VALUES (?,1,100.0,0.05,0.01,-0.01,0)", (slot,))
    try:
        labels.assert_ordered(conn)
    except ValueError:
        return
    raise AssertionError("어긋난 라벨을 통과시켰다")


@case("숏은 롱의 거울이다")
def _():
    ret, mfe, mae = 0.02, 0.05, -0.03
    assert labels.short_view(ret, mfe, mae) == (-0.02, 0.03, -0.05)

for name in PASSED:
    print(f"  통과  {name}")
for name, why in FAILED:
    print(f"  실패  {name}\n        {why}")
print(f"\n{len(PASSED)} 통과 / {len(FAILED)} 실패")
sys.exit(1 if FAILED else 0)
