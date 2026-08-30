"""분위 경계를 흔들어 본다.

Phase 3 이 남긴 후보는 하나이고, **그 우위는 경계 하나에 걸려 있다**(테이커 비율 1.4625).
`docs/adr/021` 이 감도표에서 배운 문장이 정확히 이 자리를 겨눈다 — *한 칸 바꿔서 결과가
뒤집히는 파라미터는 전략이 아니라 잡음을 고르고 있다.* 그래서 손익 곡선을 그리기 전에 경계를
흔들어 **부호가 유지되는지**부터 본다.

셋을 흔든다.

1. **분위 수** — 4·5·6·8·10. 극단 칸의 우위가 분위 수에 따라 어떻게 달라지나.
2. **문턱** — 상위·하위 p% 를 5%에서 50%까지. **매끄럽게 줄어들면 그 우위는 변수의 성질이고,
   특정 p 에서만 튀면 경계를 고른 것이다.** 이것이 이 파일의 핵심 질문이다.
3. **연도** — Phase 3 이 설명하지 못한 것이 하나 있다. 구간 밖 우위(±11%p)가 구간 안(±4.5%p)의
   **2.5배**다. 보통은 반대로 나온다. 구간 밖은 최근 30% 이므로, 우위가 시대마다 다르면
   그것이 답이다.

**3번 표는 예측 절차가 아니다.** 경계를 전 구간에서 얻어 각 해에 씌우므로 그 해의 값이 경계에
섞인다 — 그대로 매매 규칙이 될 수 없다. 여기서 묻는 것은 "이 규칙이 해마다 통했나" 가 아니라
**"우위가 시대마다 얼마나 다른가"** 이고, 그 질문에는 고정된 경계가 맞다. 앞을 보고 고르는
절차가 필요해지는 지점은 파라미터를 **고를 때**이고 여기서는 아직 고르지 않는다.

**판정을 내지 않는다.** `config.py` 의 기준 넷은 `measure.py` 가 쓰고, 이 파일은 그 결과가
경계에 얼마나 기대고 있는지만 잰다. 여기서 새 기준을 만들면 그것은 분석 뒤에 기준을 만드는
일이다.

    python stability.py                          # 후보 하나(테이커 비율), 1시간
    python stability.py open_interest 24         # 지표와 기간을 지정
"""

import sys

import pandas as pd

import store
from config import MIN_SAMPLES, QUANTILES
from measure import load, two_proportion_p

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

#: Phase 3 에서 상관 클러스터를 지나 독립 증거로 남은 지표. 기본값일 뿐이고 인자로 바꾼다.
DEFAULT_INDICATOR = "taker_buy_sell_ratio"

#: 흔들어 볼 분위 수. 5(`QUANTILES`)를 가운데 두고 양쪽으로 벌린다.
QUANTILE_SWEEP = (4, 5, 6, 8, 10)

#: 흔들어 볼 문턱(%). 상위 p% 와 하위 p% 를 각각 본다. 50 은 절반이므로 그 지점에서 두 값이
#: 정확히 반대 부호로 만나는 것이 정상이다 — 만나지 않으면 계산이 틀린 것이다.
THRESHOLD_SWEEP = (5, 10, 15, 20, 25, 30, 40, 50)


def _edge(sub, mask):
    """`mask` 로 고른 칸의 (표본, 승률 %, 우위 %p, p). 나머지 전부와 비교한다."""
    wins = sub["ret"] > 0
    n1 = int(mask.sum())
    if n1 < MIN_SAMPLES:
        return {"n": n1, "winrate": None, "edge_pp": None, "p": None}
    k1 = int(wins[mask].sum())
    n2 = len(sub) - n1
    k2 = int(wins.sum()) - k1
    baseline = int(wins.sum()) / len(sub)
    return {
        "n": n1,
        "winrate": k1 / n1 * 100,
        "edge_pp": (k1 / n1 - baseline) * 100,
        "p": two_proportion_p(k1, n1, k2, n2),
    }


def sweep_quantiles(frame, name):
    """분위 수를 흔든다. 극단 칸 둘만 낸다 — 가운데 칸은 원래 기준선 근처다."""
    sub = frame[[name, "ret"]].dropna(subset=[name])
    out = []
    for q in QUANTILE_SWEEP:
        ranks = sub[name].rank(method="average", pct=True)
        낮음 = _edge(sub, ranks <= 1 / q)
        높음 = _edge(sub, ranks > 1 - 1 / q)
        out.append({"quantiles": q, "low": 낮음, "high": 높음})
    return out


def sweep_threshold(frame, name):
    """문턱을 흔든다. **이 표의 모양이 답이다** — 매끄러운가, 한 곳에서 튀는가."""
    sub = frame[[name, "ret"]].dropna(subset=[name])
    ranks = sub[name].rank(method="average", pct=True)
    out = []
    for pct in THRESHOLD_SWEEP:
        out.append({
            "pct": pct,
            "low": _edge(sub, ranks <= pct / 100),
            "high": _edge(sub, ranks > 1 - pct / 100),
        })
    return out


def by_year(frame, name, quantiles=QUANTILES):
    """연도별 극단 칸. **경계는 전 구간에서 얻어 고정한다** — 첫 주석의 3번을 본다."""
    sub = frame[["slot_ts", name, "ret"]].dropna(subset=[name]).copy()
    if len(sub) < MIN_SAMPLES:
        return []
    낮은경계 = sub[name].quantile(1 / quantiles)
    높은경계 = sub[name].quantile(1 - 1 / quantiles)
    sub["year"] = pd.to_datetime(sub["slot_ts"], unit="ms", utc=True).dt.year

    out = []
    for year, chunk in sub.groupby("year"):
        out.append({
            "year": int(year),
            "n": len(chunk),
            "low": _edge(chunk, chunk[name] <= 낮은경계),
            "high": _edge(chunk, chunk[name] > 높은경계),
        })
    return out


def by_year_relative(frame, name, quantiles=QUANTILES):
    """연도별 극단 칸. **경계를 해마다 그 해 안에서 다시 얻는다.**

    고정 경계 표와 나란히 놓아야 하나를 가를 수 있다 — **관계가 뒤집힌 것**인가, 아니면
    **분포가 옮겨간 것**인가. 지표의 분포가 해마다 이동하면 고정 경계의 "상위 칸" 은 해마다
    다른 분위를 뜻하게 되고, 그러면 부호가 뒤집힌 것처럼 보일 수 있다. 이 표는 해마다 같은
    분위를 보므로 그 혼동이 없다.

    **여전히 예측 절차가 아니다.** 그 해의 값으로 그 해의 경계를 얻으므로 미래를 본다.
    묻는 것은 "해마다 통했나" 가 아니라 "관계 자체가 그 해에 어느 방향이었나" 다.
    """
    sub = frame[["slot_ts", name, "ret"]].dropna(subset=[name]).copy()
    sub["year"] = pd.to_datetime(sub["slot_ts"], unit="ms", utc=True).dt.year

    out = []
    for year, chunk in sub.groupby("year"):
        if len(chunk) < MIN_SAMPLES:
            continue
        낮은경계 = chunk[name].quantile(1 / quantiles)
        높은경계 = chunk[name].quantile(1 - 1 / quantiles)
        out.append({
            "year": int(year),
            "n": len(chunk),
            "low": _edge(chunk, chunk[name] <= 낮은경계),
            "high": _edge(chunk, chunk[name] > 높은경계),
        })
    return out


def _cell(edge):
    if edge["edge_pp"] is None:
        return f"{edge['n']:>7,}  표본 미달"
    return (f"{edge['n']:>7,}  {edge['winrate']:>6.2f}%  {edge['edge_pp']:>+6.2f}%p  "
            f"p {edge['p']:.4f}")


def render(name, hours, quantile_rows, threshold_rows, year_rows, year_rel_rows):
    print(f"\n{name} · {hours}시간 — 경계를 흔든다\n")

    print("  분위 수를 흔든다 (극단 칸 둘)")
    print(f"    {'분위':>4}  {'하위 칸':>34}  {'상위 칸':>34}")
    for row in quantile_rows:
        print(f"    {row['quantiles']:>4}  {_cell(row['low']):>34}  {_cell(row['high']):>34}")

    print("\n  문턱을 흔든다 — 매끄러우면 변수의 성질, 한 곳에서 튀면 경계를 고른 것이다")
    print(f"    {'문턱':>4}  {'하위 p%':>34}  {'상위 p%':>34}")
    for row in threshold_rows:
        print(f"    {row['pct']:>3}%  {_cell(row['low']):>34}  {_cell(row['high']):>34}")

    for 제목, rows in (("경계는 전 구간 고정", year_rows),
                      ("경계를 해마다 다시 얻음", year_rel_rows)):
        if not rows:
            continue
        print(f"\n  연도별 ({제목} — 예측 절차가 아니라 시대 비교다)")
        print(f"    {'연도':>4}  {'표본':>8}  {'하위 칸':>34}  {'상위 칸':>34}")
        for row in rows:
            print(f"    {row['year']:>4}  {row['n']:>8,}  "
                  f"{_cell(row['low']):>34}  {_cell(row['high']):>34}")


def main(argv):
    name = argv[0] if argv else DEFAULT_INDICATOR
    hours = int(argv[1]) if len(argv) > 1 else 1

    with store.connect() as conn:
        frame = load(conn, hours)

    if name not in frame.columns:
        print(f"모르는 지표: {name}")
        return 1
    if frame[name].notna().sum() < MIN_SAMPLES:
        print(f"{name} 표본 {int(frame[name].notna().sum()):,} < {MIN_SAMPLES:,} — 데이터 부족")
        return 0

    render(
        name, hours,
        sweep_quantiles(frame, name),
        sweep_threshold(frame, name),
        by_year(frame, name),
        by_year_relative(frame, name),
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
