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


for name in PASSED:
    print(f"  통과  {name}")
for name, why in FAILED:
    print(f"  실패  {name}\n        {why}")
print(f"\n{len(PASSED)} 통과 / {len(FAILED)} 실패")
sys.exit(1 if FAILED else 0)
