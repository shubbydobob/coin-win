"""조합 측정의 게이트가 실제로 발동하는지 본다.

`test_guards.py` 와 같은 태도다 — **규칙이 있다는 것과 그것이 막는다는 것은 다른 사실이다.**
여기 픽스처들은 상상해서 만든 것이 아니라 `measure.py` 가 이미 한 번 데인 모양을 쌍으로
옮겨 온 것이다(결측이 우위로 둔갑하는 자리).

    python test_pairs.py
"""

import math
import sys

import pandas as pd

import measure
import pairs
from config import MIN_SAMPLES, ROUND_TRIP_COST_PCT

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

PASSED, FAILED = [], []
N = MIN_SAMPLES * 6


def case(name):
    def wrap(fn):
        try:
            fn()
            PASSED.append(name)
        except Exception as e:                            # noqa: BLE001
            FAILED.append((name, f"{type(e).__name__}: {e}"))
        return fn
    return wrap


def frame_of(**cols):
    size = len(next(iter(cols.values())))
    base = {"slot_ts": list(range(0, size * 900_000, 900_000)),
            "mfe": [0.01] * size, "mae": [-0.01] * size}
    return pd.DataFrame({**base, **cols})


# ── 칸 ──────────────────────────────────────────────────────────────────────

@case("숫자 지표는 극단 둘만 칸이 된다 — 가운데는 쌍에 들어가지 않는다")
def _():
    values = pd.Series(range(N), dtype=float)
    cells, names = pairs.cells_of(values, "rsi_4h")
    assert names == ["Q1", "Q5"], names
    assert cells.iloc[0] == "Q1" and cells.iloc[-1] == "Q5"
    assert pd.isna(cells.iloc[N // 2]), "가운데 분위가 칸이 되면 표본이 25분의 1로 갈린다"


@case("범주형은 값 하나가 한 칸이다 — 극단 둘로 줄이면 '안' 이 사라진다")
def _():
    values = pd.Series(["ABOVE", "INSIDE", "BELOW"] * (N // 3))
    cells, names = pairs.cells_of(values, "cloud_position_4h")
    assert set(names) == {"ABOVE", "INSIDE", "BELOW"}, names
    assert set(cells.dropna()) == {"ABOVE", "INSIDE", "BELOW"}


@case("표본이 모자란 지표는 칸을 아예 안 만든다")
def _():
    values = pd.Series(range(MIN_SAMPLES - 1), dtype=float)
    _, names = pairs.cells_of(values, "rsi_4h")
    assert names == [], "칸을 만들면 아무것도 모른다는 사실보다 더 많이 아는 것처럼 보인다"


# ── 기준선 (이 모듈에서 가장 위험한 자리) ────────────────────────────────────

@case("기준선은 두 지표가 **둘 다** 값을 가진 행에서만 나온다")
def _():
    # 진 행들에서만 B 가 비어 있다. 전체(A 기준) 승률은 50% 인데
    # **둘 다 있는 행** 의 승률은 100% 다 — 기준선을 잘못 잡으면 네 칸이 나란히 +50%p 로 보인다.
    half = N // 2
    # 진 행들에서만 B 가 비어 있다. B 를 A 와 어긋나게 둬서 네 칸이 전부 차게 한다.
    frame = frame_of(
        a=[float(i) for i in range(N)],
        b=[float((i * 7) % half) for i in range(half)] + [None] * half,
        ret=[0.01] * half + [-0.01] * half,
    )
    rows = pairs.measure_pair(frame, "a", "b", singles={})
    assert rows, "표본이 충분한데 줄이 안 나왔다"
    filled = [r for r in rows if r["n"] > 0]
    assert len(filled) == 4, f"네 칸이 다 차야 이 검사가 뜻을 갖는다: {[r['n'] for r in rows]}"
    for row in filled:
        assert abs(row["baseline"] - 1.0) < 1e-9, (
            f"기준선이 {row['baseline']:.3f} 다 — 결측 행이 섞였다")
        assert abs(row["edge_pp"]) < 1e-9, (
            f"우위가 {row['edge_pp']:.2f}%p 다 — 결측이 우위로 둔갑했다")


@case("결측 행이 섞인 기준선을 쓰면 실제로 거짓 우위가 나온다 (위반 픽스처)")
def _():
    half = N // 2
    frame = frame_of(
        a=[float(i) for i in range(N)],
        b=[float((i * 7) % half) for i in range(half)] + [None] * half,
        ret=[0.01] * half + [-0.01] * half,
    )
    wrong_baseline = (frame["ret"] > 0).mean()          # A 만 보고 잡은 기준선
    rows = [r for r in pairs.measure_pair(frame, "a", "b", singles={}) if r["n"] > 0]
    fake_edge = (rows[0]["winrate"] - wrong_baseline) * 100
    assert fake_edge > 40, (
        "이 픽스처는 거짓 우위를 만들어야 한다 — 안 만들면 위 검사가 무엇도 증명하지 않는다")


# ── 쌍 고르기 ────────────────────────────────────────────────────────────────

@case("닮은 둘은 쌍이 되지 않는다 — 같은 것을 두 번 세는 것은 조합이 아니다")
def _():
    same = [float(i) for i in range(N)]
    frame = frame_of(a=same, b=[x + 0.001 for x in same], ret=[0.01] * N)
    assert pairs.correlated(frame, "a", "b") is True
    assert pairs.candidate_pairs(frame, ["a", "b"]) == []


@case("닮지 않은 둘은 쌍이 된다")
def _():
    frame = frame_of(
        a=[float(i % 7) for i in range(N)],
        b=[float((i * 13) % 11) for i in range(N)],
        ret=[0.01] * N,
    )
    assert pairs.correlated(frame, "a", "b") is False
    assert pairs.candidate_pairs(frame, ["a", "b"]) == [("a", "b")]


@case("범주형은 상관을 재지 않는다 — 구름의 순서는 크기가 아니다")
def _():
    frame = frame_of(
        cloud_position_4h=["ABOVE", "BELOW"] * (N // 2),
        rsi_4h=[float(i % 2) for i in range(N)],
        ret=[0.01] * N,
    )
    assert pairs.correlated(frame, "cloud_position_4h", "rsi_4h") is False


# ── 검정과 판정 ──────────────────────────────────────────────────────────────

@case("표본 미달 칸은 검정하지 않는다 — p 를 내면 남의 문턱을 움직인다")
def _():
    # A 의 Q1 과 B 의 Q1 이 겹치는 행이 거의 없게 만든다.
    frame = frame_of(
        a=[float(i) for i in range(N)],
        b=[float(N - i) for i in range(N)],
        ret=[0.01 if i % 2 else -0.01 for i in range(N)],
    )
    rows = pairs.measure_pair(frame, "a", "b", singles={})
    thin = [r for r in rows if r["n"] < MIN_SAMPLES]
    assert thin, "이 픽스처는 얇은 칸을 만들어야 한다"
    assert all(r["p_raw"] is None for r in thin)
    assert all(measure.verdict(r) == "데이터 부족" for r in thin)


@case("판정은 measure.verdict 를 그대로 쓴다 — 조합이라고 문턱이 달라지지 않는다")
def _():
    row = {"n": MIN_SAMPLES * 2, "edge_pp": 9.0, "mean_mfe": 1.0, "mean_mae": -1.0,
           "p_bh": 0.0, "mean_ret": 0.05, "baseline_ret": 0.0, "lift": 99.0}
    assert measure.verdict(row) == "비용을 못 넘는다", (
        "lift 가 커도 금액이 비용을 못 넘으면 후보가 아니다")
    row["mean_ret"] = ROUND_TRIP_COST_PCT + 0.01
    assert measure.verdict(row) == "유효 후보"


@case("lift 는 판정에 쓰이지 않는다 — 있으나 없으나 판정이 같다")
def _():
    row = {"n": MIN_SAMPLES * 2, "edge_pp": 9.0, "mean_mfe": 1.0, "mean_mae": -1.0,
           "p_bh": 0.0, "mean_ret": ROUND_TRIP_COST_PCT + 0.01, "baseline_ret": 0.0}
    with_lift = measure.verdict({**row, "lift": -50.0})
    without = measure.verdict(row)
    assert with_lift == without == "유효 후보", (with_lift, without)


@case("lift 는 더 나은 다리를 뺀 값이다 — 다리를 모르면 비워 둔다")
def _():
    singles = {("a", "Q1"): 0.30, ("b", "Q5"): -0.50}
    assert abs(pairs._lift(0.80, 0.0, singles, "a", "Q1", "b", "Q5") - 0.30) < 1e-9
    assert pairs._lift(0.80, 0.0, {}, "a", "Q1", "b", "Q5") is None, (
        "0 으로 적으면 '단독과 같다' 는 없는 사실이 생긴다")


@case("NaN 은 저장하지 않는다 — NULL 과 섞이면 표가 거짓말한다")
def _():
    assert pairs._clean(float("nan")) is None
    assert pairs._clean(None) is None
    assert pairs._clean(0.0) == 0.0, "0 은 값이다. 비워 버리면 관측이 사라진다"


# ── 구간 밖 ──────────────────────────────────────────────────────────────────

@case("구간 밖은 경계를 학습 구간에서만 얻는다")
def _():
    frame = frame_of(
        a=[float(i % 100) for i in range(N)],
        b=[float((i * 7) % 100) for i in range(N)],
        ret=[0.01 if i % 3 else -0.01 for i in range(N)],
    )
    out = pairs.out_of_sample_pair(frame, "a", "b")
    assert out, "학습·검증 둘 다 표본이 충분한데 비어 있다"
    for (lcell, rcell), (n, edge) in out.items():
        assert lcell in ("Q1", "Q5") and rcell in ("Q1", "Q5")
        if n < MIN_SAMPLES:
            assert math.isnan(edge), "표본이 모자란데 수를 냈다"


@case("검증 구간이 학습 구간과 겹치지 않는다 (금지 구간)")
def _():
    frame = frame_of(
        a=[float(i % 50) for i in range(N)],
        b=[float((i * 3) % 50) for i in range(N)],
        ret=[0.01] * N,
    )
    # 금지 구간이 0 이면 학습 마지막 슬롯의 24시간 라벨이 검증 첫 슬롯들과 같은 시간을 본다.
    assert pairs.EMBARGO_HOURS > 0
    out = pairs.out_of_sample_pair(frame, "a", "b")
    assert isinstance(out, dict)


for name in PASSED:
    print(f"  통과  {name}")
for name, why in FAILED:
    print(f"  실패  {name}\n        {why}")
print(f"\n{len(PASSED)} 통과 / {len(FAILED)} 실패")
sys.exit(1 if FAILED else 0)
