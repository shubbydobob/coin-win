"""게이트가 실제로 발동하는지 본다. **통과하는 것처럼 보이는 검사가 가장 위험하다.**

이 저장소는 규칙을 세울 때 위반 픽스처를 함께 둔다(`archFixture`). 여기도 같은 태도다 —
룩어헤드 검사가 있다는 것과 그것이 실제로 막는다는 것은 다른 사실이다.

    python test_guards.py
"""

import math
import sqlite3
import sys
import tempfile
from pathlib import Path

import pandas as pd

import dumps
import labels
import stability
import indicators
import measure
import pandas as pd
import snapshots
import store
from config import ALPHA, ERA_BOUNDARY, LIQUIDATION_DISTANCE_PCT, MIN_SAMPLES

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

# ── 측정 (Phase 3) ──────────────────────────────────────────────────────────
#
# **검사가 있다는 것과 실제로 막는다는 것은 다른 사실이다.** 아래는 전부 위반 픽스처다 —
# 판정이 무너지는 모양을 손으로 만들어 놓고 규칙이 그것을 잡는지 본다. 통계 코드는 그럴듯한
# 숫자를 언제나 내므로(Phase 4 의 밀린 구름과 같다) 눈으로 봐서는 무너진 것을 알 수 없다.


def _flat_labels(n, ret):
    return {"ret": ret, "mfe": [0.02] * n, "mae": [-0.02] * n}


@case("표본이 모자란 지표는 분위를 나누지 않고 결론도 내지 않는다")
def _():
    n = MIN_SAMPLES - 1
    frame = pd.DataFrame({
        "book_imbalance": [i / n for i in range(n)],
        **_flat_labels(n, [0.01] * n),          # 승률 100%. 우위가 최대인데도
    })
    rows = measure.measure_indicator(frame, "book_imbalance")
    assert len(rows) == 1, f"표본 미달인데 분위를 나눴다: {len(rows)}줄"
    assert rows[0]["p_raw"] is None, "표본 미달인데 검정을 했다"
    assert measure.verdict(rows[0]) == "데이터 부족", measure.verdict(rows[0])


@case("표본 미달 분위는 아무리 유의해도 결론이 되지 않는다")
def _():
    # 판정 순서가 뒤집히면 이 줄이 "유효 후보" 가 된다. 우위 20%p 에 p 는 0 이다.
    row = {"n": MIN_SAMPLES - 1, "edge_pp": 20.0,
           "mean_mfe": 1.0, "mean_mae": -0.5, "p_bh": 0.0}
    assert measure.verdict(row) == "데이터 부족", measure.verdict(row)


@case("평균 MAE 가 청산 거리를 넘으면 승률과 무관하게 사용 불가다")
def _():
    big = MIN_SAMPLES * 10
    long_side = {"n": big, "edge_pp": +20.0, "mean_mfe": 30.0,
                 "mean_mae": LIQUIDATION_DISTANCE_PCT - 0.01, "p_bh": 0.0}
    assert measure.verdict(long_side) == "사용 불가", measure.verdict(long_side)
    # 숏 후보(우위가 음수)는 롱 기준 MFE 를 뒤집어 본다 - 올라간 거리가 숏에게는 손실이다.
    # 이 줄의 롱 기준 MAE 는 -0.1% 로 아주 얕아서, 방향을 안 보면 그냥 통과한다.
    short_side = {"n": big, "edge_pp": -20.0, "mean_mfe": -LIQUIDATION_DISTANCE_PCT + 0.01,
                  "mean_mae": -0.1, "p_bh": 0.0}
    assert measure.verdict(short_side) == "사용 불가", measure.verdict(short_side)


@case("BH 보정은 순진한 p 보다 언제나 보수적이다")
def _():
    ps = [0.001, 0.01, 0.02, 0.04, 0.2, 0.5, 0.9]
    qs = measure.benjamini_hochberg(ps)
    assert all(q >= p - 1e-12 for p, q in zip(ps, qs)), list(zip(ps, qs))
    naive = sum(p < ALPHA for p in ps)
    corrected = sum(q < ALPHA for q in qs)
    assert corrected < naive, f"보정 뒤에도 {corrected}개가 살았다 (순진하게는 {naive}개)"


@case("검정이 많으면 BH 가 아슬아슬한 하나를 지운다")
def _():
    # 50개를 훑어 p=0.04 하나를 건진 상황. 아무 관계가 없어도 50개 중 둘은 이보다 작다.
    ps = [0.04] + [0.5] * 49
    qs = measure.benjamini_hochberg(ps)
    assert ps[0] < ALPHA and qs[0] >= ALPHA, f"보정 뒤에도 {qs[0]:.4f} 로 살아남았다"


@case("상관이 높은 두 지표는 한 클러스터로 묶여 한 번만 센다")
def _():
    n = MIN_SAMPLES * 2
    base = [math.sin(i / 7.0) for i in range(n)]
    frame = pd.DataFrame({
        "open_interest": base,
        # 단조 변환이므로 순위가 완전히 같다. 같은 사실을 두 번 말하는 지표의 극단이다.
        "open_interest_value": [x * 1000.0 + 5.0 for x in base],
        "long_short_account_ratio": [((i * 7919) % n) / n for i in range(n)],
    })
    names = list(frame.columns)
    clusters = measure.correlation_clusters(frame, names)
    assert clusters["open_interest"] == clusters["open_interest_value"], clusters
    assert clusters["long_short_account_ratio"] != clusters["open_interest"], clusters
    counts = {c: int(frame[c].notna().sum()) for c in names}
    reps = measure.representatives(clusters, counts)
    duplicated = {"open_interest", "open_interest_value"} & reps
    assert len(duplicated) == 1, f"중복 증거를 둘 다 셌다: {duplicated}"
    assert "long_short_account_ratio" in reps, "묶이지 않은 지표가 대표에서 빠졌다"


@case("기준선은 그 지표가 값을 가진 행에서만 나온다")
def _():
    n = MIN_SAMPLES * 20
    half = n // 2
    # 앞 절반은 지표가 비어 있고 그 구간은 전부 오른다. 전체 승률은 75%, 지표가 값을 가진
    # 구간의 승률은 50% 다. 전체 승률과 대면 다섯 분위가 나란히 -25%p 로 보인다.
    frame = pd.DataFrame({
        "open_interest": [None] * half + [float(i) for i in range(half)],
        **_flat_labels(n, [0.01] * half + [0.01 if i % 2 else -0.01 for i in range(half)]),
    })
    rows = measure.measure_indicator(frame, "open_interest")
    assert all(abs(r["baseline"] - 0.5) < 0.01 for r in rows), rows[0]["baseline"]
    assert all(abs(r["edge_pp"]) < 3.0 for r in rows), [r["edge_pp"] for r in rows]


@case("구간 밖 확인의 분위 경계는 학습 구간에서만 나온다")
def _():
    n = MIN_SAMPLES * 20
    frame = pd.DataFrame({
        "slot_ts": [_slot0() + i * store.SLOT_MS for i in range(n)],
        "open_interest": [float(i) for i in range(n)],   # 단조 증가 = 가장 심한 수준값
        "ret": [0.01 if i % 2 else -0.01 for i in range(n)],
    })
    out = measure.out_of_sample(frame, "open_interest")
    counts = {q: rows for q, (rows, _) in out.items()}
    top = max(counts)
    assert counts[top] == sum(counts.values()), counts
    # 픽스처가 실제로 구분력을 갖는지 본다 - 전체 구간에서 경계를 얻었다면 검증 구간이
    # 여러 분위에 흩어진다. 그러지 않으면 위 단언은 아무것도 증명하지 않는다.
    codes, _edges = measure._bins(frame["open_interest"])
    tail = codes[-sum(counts.values()):]
    assert tail.nunique() > 1, "픽스처가 경계 누수를 구분하지 못한다"


@case("검증 구간의 칸이 표본 미달이면 구간 밖 우위를 내지 않는다")
def _():
    n = MIN_SAMPLES * 20
    frame = pd.DataFrame({
        "slot_ts": [_slot0() + i * store.SLOT_MS for i in range(n)],
        "open_interest": [float(i) for i in range(n)],
        "ret": [0.01 if i % 2 else -0.01 for i in range(n)],
    })
    out = measure.out_of_sample(frame, "open_interest")
    empty = [q for q, (rows, _) in out.items() if rows < MIN_SAMPLES]
    assert empty, "표본 미달인 칸이 없어 이 검사가 아무것도 보지 않는다"
    assert all(math.isnan(out[q][1]) for q in empty), \
        "몇십 행짜리 칸에서 우위를 냈다"



# ── 경계 흔들기 (stability) ─────────────────────────────────────────────────

def _drift_frame(n=6000, 뒤집는가=False):
    """지표와 수익률이 붙어 있는 합성 표. `뒤집는가` 면 뒤쪽 절반의 관계가 반대가 된다."""
    import pandas as pd
    # 두 해로 갈라 놓는다. 한 해에 다 넣으면 연도 표에 줄이 하나뿐이라 부호가 갈릴 자리가 없다.
    해시작 = (1_609_459_200_000, 1_735_689_600_000)          # 2021-01-01, 2025-01-01 (UTC)
    지표, 수익, 시각 = [], [], []
    for i in range(n):
        x = (i * 37 % 1000) / 1000.0
        뒤쪽 = i >= n // 2
        방향 = -1 if (뒤집는가 and 뒤쪽) else 1
        지표.append(x)
        수익.append(방향 * (x - 0.5) * 0.01)
        시각.append(해시작[1 if 뒤쪽 else 0] + (i % (n // 2)) * store.SLOT_MS)
    return pd.DataFrame({"slot_ts": 시각, "taker_buy_sell_ratio": 지표, "ret": 수익})


@case("문턱 50% 에서 상위와 하위가 반대 부호로 만난다")
def _():
    rows = stability.sweep_threshold(_drift_frame(), "taker_buy_sell_ratio")
    절반 = [r for r in rows if r["pct"] == 50][0]
    합 = 절반["low"]["edge_pp"] + 절반["high"]["edge_pp"]
    assert abs(합) < 1e-9, f"절반에서 두 칸이 만나지 않는다: {합}"


@case("해마다 다시 얻은 경계는 칸 크기를 해마다 같게 만든다")
def _():
    frame = _drift_frame()
    rows = stability.by_year_relative(frame, "taker_buy_sell_ratio")
    assert rows, "연도 표가 비었다"
    for row in rows:
        기대 = round(row["n"] / 5)
        assert abs(row["low"]["n"] - 기대) <= 2, (row["year"], row["low"]["n"], 기대)


@case("관계가 뒤집히면 연도 표가 부호로 그것을 드러낸다")
def _():
    # 해마다 6,000 이어야 극단 칸(1/5)이 1,200 으로 MIN_SAMPLES 를 넘는다.
    rows = stability.by_year_relative(_drift_frame(12000, 뒤집는가=True), "taker_buy_sell_ratio")
    부호 = [r["high"]["edge_pp"] for r in rows if r["high"]["edge_pp"] is not None]
    assert any(a > 0 for a in 부호) and any(a < 0 for a in 부호), (
        f"뒤집었는데 부호가 한쪽뿐이다: {부호}")


@case("표본이 모자란 해는 연도 표에서 빠진다")
def _():
    작은표 = _drift_frame(n=2400)          # 두 해로 갈려 해마다 1,200 → 남고
    잔표 = _drift_frame(n=600)             # 한 해가 600 → 빠진다
    assert stability.by_year_relative(작은표, "taker_buy_sell_ratio")
    assert not stability.by_year_relative(잔표, "taker_buy_sell_ratio")

# ── 판독 지표 (docs/spec/indicator-usage.md) ────────────────────────────────

@case("슬롯보다 늦게 닫힌 봉을 붙이면 저장이 거절한다")
def _():
    conn = _fresh()
    conn.executescript(
        "CREATE TABLE indicator_h4 (slot_ts INTEGER PRIMARY KEY, "
        "bar_open_time INTEGER NOT NULL, bar_close_time INTEGER NOT NULL, rsi REAL);"
    )
    slot = _slot0()
    conn.execute("INSERT INTO indicator_h4 VALUES (?,?,?,?)", (slot, slot, slot, 50.0))
    try:
        indicators.assert_no_lookahead(conn, "4h")
    except indicators.LookaheadError:
        return
    raise AssertionError("거절하지 않았다 — 봉 마감이 슬롯과 같은 것도 아직 닫히지 않은 것이다")


@case("슬롯 직전에 닫힌 봉은 통과한다")
def _():
    conn = _fresh()
    conn.executescript(
        "CREATE TABLE indicator_h4 (slot_ts INTEGER PRIMARY KEY, "
        "bar_open_time INTEGER NOT NULL, bar_close_time INTEGER NOT NULL, rsi REAL);"
    )
    slot = _slot0()
    conn.execute("INSERT INTO indicator_h4 VALUES (?,?,?,?)", (slot, slot - 100, slot - 1, 50.0))
    indicators.assert_no_lookahead(conn, "4h")


@case("붙이는 규칙은 그 시각에 이미 닫힌 마지막 봉을 고른다")
def _():
    slot = _slot0()
    rows = [
        (slot - 3000, slot - 2001, {"rsi": 10.0}),      # 슬롯보다 한참 전에 닫힘
        (slot - 2000, slot - 1, {"rsi": 20.0}),         # 슬롯 직전에 닫힘 — 이것이 답이다
        (slot - 1000, slot + 999, {"rsi": 30.0}),       # 슬롯 뒤에 닫힘 — 아직 모른다
    ]
    original = indicators.read
    indicators.read = lambda interval: rows
    try:
        attached = indicators.attach(None, "4h", [slot])
    finally:
        indicators.read = original
    assert len(attached) == 1, attached
    assert attached[0][1][2]["rsi"] == 20.0, attached[0][1][2]


@case("범주형은 분위로 자르지 않고 값 하나가 한 칸이 된다")
def _():
    frame = pd.DataFrame({
        "cloud_position_4h": ["ABOVE"] * 4000 + ["INSIDE"] * 4000 + ["BELOW"] * 4000,
        "ret": [0.01] * 6000 + [-0.01] * 6000,
        "mfe": [0.02] * 12000,
        "mae": [-0.02] * 12000,
    })
    rows = measure.measure_indicator(frame, "cloud_position_4h")
    assert len(rows) == 3, f"칸이 셋이어야 한다: {len(rows)}"
    assert {r["label"] for r in rows} == {"ABOVE", "INSIDE", "BELOW"}
    assert all(r["lo"] is None for r in rows), "범주에는 구간이 없다"


@case("승률 기준을 넘어도 금액이 비용을 못 넘으면 후보가 아니다")
def _():
    row = {"n": MIN_SAMPLES, "edge_pp": 10.0, "mean_mfe": 1.0, "mean_mae": -1.0,
           "p_bh": 0.0, "mean_ret": 0.10, "baseline_ret": 0.0}
    assert measure.verdict(row) == "비용을 못 넘는다", measure.verdict(row)
    row["mean_ret"] = 0.20                       # 기준선 대비 0.20% > 왕복 0.14%
    assert measure.verdict(row) == "유효 후보", measure.verdict(row)


@case("기준선을 빼지 않으면 시장 상승이 신호로 읽힌다")
def _():
    # 분위와 기준선이 똑같이 +0.20% 다. 빼지 않으면 비용을 넘은 것처럼 보인다.
    row = {"n": MIN_SAMPLES, "edge_pp": 10.0, "mean_mfe": 1.0, "mean_mae": -1.0,
           "p_bh": 0.0, "mean_ret": 0.20, "baseline_ret": 0.20}
    assert measure.verdict(row) == "비용을 못 넘는다", measure.verdict(row)


@case("국면 경계는 두 구간을 겹치지 않게 가른다")
def _():
    boundary = int(pd.Timestamp(ERA_BOUNDARY, tz="UTC").timestamp() * 1000)
    assert measure.era_bounds("all") == (None, None)
    assert measure.era_bounds("reversion") == (None, boundary)
    assert measure.era_bounds("momentum") == (boundary, None)


@case("범주형은 상관 클러스터에서 빠지지만 대표 자리는 지킨다")
def _():
    frame = pd.DataFrame({
        "cloud_position_4h": ["ABOVE"] * 6000 + ["BELOW"] * 6000,
        "rsi_4h": list(range(12000)),
    })
    clusters = measure.correlation_clusters(frame, ["cloud_position_4h", "rsi_4h"])
    assert "cloud_position_4h" in clusters, "클러스터가 없으면 독립 증거에서 사라진다"
    reps = measure.representatives(clusters, {"cloud_position_4h": 6000, "rsi_4h": 12000})
    assert "cloud_position_4h" in reps


for name in PASSED:
    print(f"  통과  {name}")
for name, why in FAILED:
    print(f"  실패  {name}\n        {why}")
print(f"\n{len(PASSED)} 통과 / {len(FAILED)} 실패")
sys.exit(1 if FAILED else 0)
