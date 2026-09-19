"""조합 측정 — 지표 **둘을 겹쳤을 때** 달라지는가.

`measure.py` 가 지표를 하나씩 쟀고 답은 일관됐다 — 승률로는 이기는 칸이 여럿인데 **금액에서
왕복 비용(0.14%)을 못 넘는다.** `research/README.md` 가 "Phase 3 이 재지 않은 것" 첫 줄에
적어 둔 것이 이 모듈이 묻는 질문이다:

> **지표 조합** — 각 지표를 따로만 봤고, 둘을 겹쳤을 때 달라지는지는 **모른다.**

**이것은 새 기준을 만드는 자리가 아니다.** 판정은 `measure.verdict` 를 그대로 부른다.
조합이라고 문턱을 낮추면 그 순간 이 파이프라인이 재는 것은 시장이 아니라 우리의 기대가 된다.

## 결과를 보기 전에 정해 둔 것 넷

**1. 겹치는 칸은 극단뿐이다.** 숫자 지표는 Q1 과 Q5 만 쓴다. 다섯 분위를 통째로 교차하면
칸이 25개가 되고 표본이 25분의 1로 갈린다. `stability.py` 가 이미 관측한 것도 같은 방향이다 —
문턱을 5%에서 50%까지 흔들어도 **극단으로 갈수록 세진다.** 범주형은 값 하나가 한 칸이므로
그대로 쓴다(구름 위/안/아래를 극단 둘로 줄이면 "안" 이 사라진다).

**2. 기준선은 두 지표가 *둘 다* 값을 가진 행에서 얻는다.** 하나만 있는 행을 섞으면 결측이
우위로 둔갑한다 — `measure.py` 가 상위 계정 비율에서 겪은 것과 **정확히 같은 함정**이고,
쌍에서는 결측이 둘 다 걸리므로 더 크게 어긋난다. `test_pairs.py` 가 그 모양을 손으로 만들어
둔다.

**3. 닮은 둘은 겹치지 않는다.** 상관 `CORRELATION_CLUSTER_THRESHOLD`(0.7) 이상인 쌍은 만들지
않는다. 같은 것을 두 번 겹치는 것은 조합이 아니라 **한 지표를 두 번 세는 것**이고,
`measure.py` 가 독립 증거를 셀 때 쓰는 기준을 여기서 그대로 쓴다.

**4. BH 가족은 쌍만으로 한 덩어리다.** 단일 측정과 합치지 않는다 — 다른 질문이고, 합치면
"둘을 겹치면 달라지는가" 의 문턱이 단일 지표 표에 달린다. `measure.run` 이 국면을 한 실행에
묶지 않는 것과 같은 이유다.

**검정 수가 많다는 것이 이 모듈의 성질이다.** 지표 69개면 쌍이 2,346개이고 칸과 기간까지
곱하면 수만 건이다. BH 문턱이 그만큼 가혹해지는데 **그것이 옳다** — 수만 번 뒤져 하나를
찾는 일에서 보정을 느슨하게 하는 것은 찾은 것이 아니라 고른 것이다.

## 판정에 쓰지 않고 적기만 하는 것 — `lift`

조합의 |평균수익률 − 기준선| 에서 **두 다리 각각의 같은 수치 중 큰 것**을 뺀 값이다.

**이것은 판정이 아니다.** 지금 문턱을 정하면 결과를 보고 정하는 것이 되기 때문이고, 그래도
찍는 이유는 사람이 한 가지를 물어야 하기 때문이다 — **조합이 새로 연 것인가, 아니면 한 다리가
혼자 하던 일을 조합의 이름으로 다시 적은 것인가.** `measure.py` 의 구간 밖 칸이 "판정이
아니다" 라고 적힌 채 옆에 붙어 있는 것과 같은 자리다.

    python pairs.py                  # 전부 재고 저장한다
    python pairs.py --report         # 재지 않고 마지막 결과만 본다
    python pairs.py --era momentum   # 한 국면만
    python pairs.py --horizon 4      # 한 기간만 (BH 가족이 줄어든다 — 확인용)
"""

import itertools
import math
import sys
import time

import pandas as pd

import measure
import store
from config import (
    CORRELATION_CLUSTER_THRESHOLD,
    EMBARGO_HOURS,
    ERAS,
    HORIZONS_HOURS,
    MIN_SAMPLES,
    QUANTILES,
    TRAIN_FRACTION,
)

MS_PER_HOUR = 3_600_000

_SCHEMA = """
CREATE TABLE IF NOT EXISTS pair_measurement (
    run_at      INTEGER NOT NULL,
    era         TEXT    NOT NULL,
    horizon_h   INTEGER NOT NULL,
    left_name   TEXT    NOT NULL,
    left_cell   TEXT    NOT NULL,
    right_name  TEXT    NOT NULL,
    right_cell  TEXT    NOT NULL,
    n           INTEGER NOT NULL,
    winrate     REAL,
    baseline    REAL,
    baseline_ret REAL,
    edge_pp     REAL,
    mean_ret    REAL,
    mean_mfe    REAL,
    mean_mae    REAL,
    lift        REAL,
    p_raw       REAL,
    p_bh        REAL,
    oos_n       INTEGER,
    oos_edge_pp REAL,
    verdict     TEXT    NOT NULL,
    PRIMARY KEY (run_at, era, horizon_h, left_name, left_cell, right_name, right_cell)
);
"""


# ── 칸 ──────────────────────────────────────────────────────────────────────

def cells_of(values, name, quantiles=QUANTILES):
    """이 지표의 칸. `(행마다의 칸 이름, 칸 이름 목록)`. 칸이 아닌 행은 `None` 이다.

    **숫자는 극단 둘만 칸이 된다.** 가운데 분위는 `None` 이라 쌍에 들어가지 않는다 —
    버리는 것이 아니라 이 모듈이 묻지 않는 자리다(§ 결정 1).

    **범주는 값 하나가 한 칸이다.** 극단 둘로 줄이면 사전순 양 끝이 남는데, 그것은
    `cloud_position` 에서 "위" 와 "안" 을 고르고 "아래" 를 버리는 꼴이다.
    """
    if measure.is_categorical(name):
        labels = sorted(values.dropna().unique().tolist(), key=str)
        return values.map(lambda v: None if pd.isna(v) else str(v)), [str(x) for x in labels]
    usable = values.dropna()
    if len(usable) < MIN_SAMPLES:
        return pd.Series([None] * len(values), index=values.index), []
    codes, edges = pd.qcut(usable, quantiles, labels=False, retbins=True, duplicates="drop")
    top = len(edges) - 2
    if top < 1:
        return pd.Series([None] * len(values), index=values.index), []
    named = codes.map(lambda q: "Q1" if q == 0 else ("Q%d" % (top + 1) if q == top else None))
    return named.reindex(values.index), ["Q1", "Q%d" % (top + 1)]


def correlated(frame, left, right, threshold=CORRELATION_CLUSTER_THRESHOLD):
    """닮은 둘인가. 겹칠 값이 없으면 겹치지 않은 것으로 본다.

    **범주형은 상관을 재지 않는다.** 순위 상관은 순서가 있는 값에만 뜻이 있고, 구름 위/안/아래
    사이의 순서는 우리가 정한 것이지 크기가 아니다.
    """
    if measure.is_categorical(left) or measure.is_categorical(right):
        return False
    both = frame[[left, right]].dropna()
    if len(both) < MIN_SAMPLES:
        return False
    # **`DataFrame.corr` 이어야 한다.** `Series.corr(method="spearman")` 은 `scipy` 를 부르고
    # 이 환경에는 그것이 없다 — `research/README.md` 가 적어 둔 함정이고, 프로브가 그 자리에서
    # 죽은 적이 있다. 두 경로가 같은 수를 내므로 안 부르는 쪽을 쓴다.
    rho = both.corr(method="spearman").iloc[0, 1]
    return bool(pd.notna(rho) and abs(rho) >= threshold)


# ── 한 쌍을 재는 일 ──────────────────────────────────────────────────────────

def measure_pair(frame, left, right, singles, quantiles=QUANTILES):
    """한 쌍을 칸 교차로 갈라 칸마다 한 줄. p 는 보정 전이고 판정도 아직 없다.

    **기준선은 둘 다 값을 가진 행에서만 나온다**(§ 결정 2). 검정은 `measure.py` 와 같은
    모양이다 — 그 칸 대 **나머지 전부**이고, 둘 다 같은 부분집합 안이라 독립이다.
    """
    sub = frame[[left, right, "ret", "mfe", "mae"]].dropna(subset=[left, right])
    total = len(sub)
    if total < MIN_SAMPLES:
        return []

    left_cells, left_names = cells_of(sub[left], left, quantiles)
    right_cells, right_names = cells_of(sub[right], right, quantiles)
    if not left_names or not right_names:
        return []

    wins = sub["ret"] > 0
    total_wins = int(wins.sum())
    baseline = total_wins / total
    baseline_ret = sub["ret"].mean() * 100

    rows = []
    for lname, rname in itertools.product(left_names, right_names):
        mask = (left_cells == lname) & (right_cells == rname)
        n1 = int(mask.sum())
        k1 = int(wins[mask].sum())
        winrate = k1 / n1 if n1 else float("nan")
        mean_ret = sub.loc[mask, "ret"].mean() * 100 if n1 else float("nan")
        rows.append({
            "left_name": left, "left_cell": lname,
            "right_name": right, "right_cell": rname,
            "n": n1,
            "winrate": winrate,
            "baseline": baseline,
            "baseline_ret": baseline_ret,
            "edge_pp": (winrate - baseline) * 100 if n1 else float("nan"),
            "mean_ret": mean_ret,
            "mean_mfe": sub.loc[mask, "mfe"].mean() * 100 if n1 else float("nan"),
            "mean_mae": sub.loc[mask, "mae"].mean() * 100 if n1 else float("nan"),
            "lift": _lift(mean_ret, baseline_ret, singles, left, lname, right, rname),
            "p_raw": measure.two_proportion_p(k1, n1, total_wins - k1, total - n1)
            if n1 >= MIN_SAMPLES else None,
        })
    return rows


def _lift(mean_ret, baseline_ret, singles, left, lcell, right, rcell):
    """조합이 두 다리 중 **더 나은 쪽**보다 얼마나 더 냈나(%p). 판정에 쓰지 않는다.

    다리의 수치를 못 찾으면 비어 둔다 — 0 으로 적으면 "단독과 같다" 는 없는 사실이 생긴다.
    """
    if any(map(lambda x: x is None or (isinstance(x, float) and math.isnan(x)),
               (mean_ret, baseline_ret))):
        return None
    legs = [singles.get((left, lcell)), singles.get((right, rcell))]
    known = [abs(x) for x in legs if x is not None]
    if not known:
        return None
    return abs(mean_ret - baseline_ret) - max(known)


def single_edges(frame, names, quantiles=QUANTILES):
    """다리 단독의 `|평균수익률 − 기준선|`. `{(지표, 칸): 값}`.

    **`measure.py` 를 다시 구현하지 않는다** — 여기 필요한 것은 한 수뿐이고, 그 수는 이
    모듈의 기준선 규칙(둘 다 값을 가진 행)이 **아니라** 단독의 규칙(그 지표가 값을 가진 행)
    으로 나와야 비교가 성립한다.
    """
    out = {}
    for name in names:
        sub = frame[[name, "ret"]].dropna(subset=[name])
        if len(sub) < MIN_SAMPLES:
            continue
        cells, cell_names = cells_of(sub[name], name, quantiles)
        base = sub["ret"].mean() * 100
        for cell in cell_names:
            mask = cells == cell
            if int(mask.sum()) >= MIN_SAMPLES:
                out[(name, cell)] = sub.loc[mask, "ret"].mean() * 100 - base
    return out


def out_of_sample_pair(frame, left, right, quantiles=QUANTILES):
    """칸 경계를 앞 구간에서 얻어 뒤 구간에 씌운다. `{(왼칸, 오른칸): (표본, 우위 %p)}`.

    **판정이 아니다.** `measure.out_of_sample` 과 같은 자리이고 같은 이유로 잰다 —
    한 구간에서 수만 쌍을 훑어 좋은 것을 집으면 그 구간에서는 정의상 언제나 좋다.
    """
    cols = ["slot_ts", left, right, "ret"]
    sub = frame[cols].dropna(subset=[left, right]).sort_values("slot_ts")
    if len(sub) < MIN_SAMPLES:
        return {}
    cutoff = sub["slot_ts"].iloc[int(len(sub) * TRAIN_FRACTION)]
    train = sub[sub["slot_ts"] < cutoff]
    valid = sub[sub["slot_ts"] >= cutoff + EMBARGO_HOURS * MS_PER_HOUR]
    if len(train) < MIN_SAMPLES or len(valid) < MIN_SAMPLES:
        return {}

    left_cells, left_names = _apply_train_cells(train, valid, left, quantiles)
    right_cells, right_names = _apply_train_cells(train, valid, right, quantiles)
    if not left_names or not right_names:
        return {}

    wins = valid["ret"] > 0
    baseline = wins.mean()
    out = {}
    for lname, rname in itertools.product(left_names, right_names):
        mask = (left_cells == lname) & (right_cells == rname)
        n = int(mask.sum())
        edge = (wins[mask].mean() - baseline) * 100 if n >= MIN_SAMPLES else float("nan")
        out[(lname, rname)] = (n, edge)
    return out


def _apply_train_cells(train, valid, name, quantiles):
    """학습 구간에서 얻은 경계를 검증 구간에 씌운다. 범주는 고를 것이 없어 값 그대로다."""
    if measure.is_categorical(name):
        return valid[name].map(lambda v: None if pd.isna(v) else str(v)), \
            [str(x) for x in sorted(train[name].dropna().unique().tolist(), key=str)]
    usable = train[name].dropna()
    if len(usable) < MIN_SAMPLES:
        return pd.Series([None] * len(valid), index=valid.index), []
    _, edges = pd.qcut(usable, quantiles, labels=False, retbins=True, duplicates="drop")
    top = len(edges) - 2
    if top < 1:
        return pd.Series([None] * len(valid), index=valid.index), []
    wide = [-math.inf, *list(edges[1:-1]), math.inf]
    codes = pd.cut(valid[name], wide, labels=False, duplicates="drop")
    named = codes.map(lambda q: None if pd.isna(q)
                      else ("Q1" if q == 0 else ("Q%d" % (top + 1) if q == top else None)))
    return named, ["Q1", "Q%d" % (top + 1)]


# ── 실행 ────────────────────────────────────────────────────────────────────

def candidate_pairs(frame, names):
    """겹칠 쌍. 닮은 둘은 빠진다(§ 결정 3)."""
    return [(a, b) for a, b in itertools.combinations(names, 2)
            if not correlated(frame, a, b)]


def run(conn, horizons=HORIZONS_HOURS, era="all"):
    """한 국면·전 쌍을 재고 BH 로 보정한 뒤 판정을 붙인다.

    **판정은 `measure.verdict` 를 그대로 부른다**(§ 첫 문단). 쌍에만 있는 칸(`lift`)은
    그 함수가 보지 않는다 — 판정에 쓰지 않기로 한 수이므로 판정 함수가 몰라야 맞다.
    """
    readout = measure.readout_columns(conn)
    names = measure.SNAPSHOT_INDICATORS + sorted(readout)
    rows = []
    for hours in horizons:
        frame = measure.load(conn, hours, readout, era)
        singles = single_edges(frame, names)
        pairs = candidate_pairs(frame, names)
        print(f"  {hours}시간: 쌍 {len(pairs)}개", flush=True)
        for left, right in pairs:
            oos = None
            for row in measure_pair(frame, left, right, singles):
                if oos is None:
                    oos = out_of_sample_pair(frame, left, right)
                row["horizon_h"] = hours
                row["era"] = era
                row["oos_n"], row["oos_edge_pp"] = oos.get(
                    (row["left_cell"], row["right_cell"]), (None, None))
                rows.append(row)

    tested = [r for r in rows if r.get("p_raw") is not None]
    for row, q in zip(tested, measure.benjamini_hochberg([r["p_raw"] for r in tested])):
        row["p_bh"] = q
    for row in rows:
        row.setdefault("p_bh", None)
        row["verdict"] = measure.verdict(row)
    return rows


def save(conn, rows, run_at):
    conn.executescript(_SCHEMA)
    conn.executemany(
        "INSERT INTO pair_measurement (run_at, era, horizon_h, left_name, left_cell, "
        "right_name, right_cell, n, winrate, baseline, baseline_ret, edge_pp, mean_ret, "
        "mean_mfe, mean_mae, lift, p_raw, p_bh, oos_n, oos_edge_pp, verdict) "
        "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT DO NOTHING",
        [
            (
                run_at, r["era"], r["horizon_h"], r["left_name"], r["left_cell"],
                r["right_name"], r["right_cell"], r["n"], _clean(r.get("winrate")),
                _clean(r.get("baseline")), _clean(r.get("baseline_ret")),
                _clean(r.get("edge_pp")), _clean(r.get("mean_ret")),
                _clean(r.get("mean_mfe")), _clean(r.get("mean_mae")),
                _clean(r.get("lift")), r.get("p_raw"), r.get("p_bh"),
                r.get("oos_n"), _clean(r.get("oos_edge_pp")), r["verdict"],
            )
            for r in rows
        ],
    )
    conn.commit()


def _clean(x):
    """`NaN` 은 저장하지 않는다 — SQLite 에서 `NULL` 과 구별되지 않게 섞이면 표가 거짓말한다."""
    return None if x is None or (isinstance(x, float) and math.isnan(x)) else x


# ── 표 ──────────────────────────────────────────────────────────────────────

def render(rows, era="all"):
    """판정별 개수와, 유효 후보만 줄로 찍는다.

    **유효 후보가 0 인 것이 정상적인 결과다.** 이 파이프라인이 두 번 그렇게 답했다.
    """
    lines = [f"[{era}] 쌍 {len({(r['left_name'], r['right_name']) for r in rows})}개 · "
             f"칸 {len(rows)}줄"]
    counts = {}
    for row in rows:
        counts[row["verdict"]] = counts.get(row["verdict"], 0) + 1
    for name, count in sorted(counts.items(), key=lambda kv: -kv[1]):
        lines.append(f"  {name}: {count}")

    winners = [r for r in rows if r["verdict"] == "유효 후보"]
    if not winners:
        lines.append("  유효 후보 없음 — 둘을 겹쳐도 비용을 못 넘는다")
        return "\n".join(lines)

    lines.append("")
    lines.append(f"{'기간':>4} {'왼쪽':<26} {'칸':<5} {'오른쪽':<26} {'칸':<5} "
                 f"{'표본':>7} {'우위%p':>8} {'수익%':>8} {'lift':>8} {'구간밖':>8}")
    for r in sorted(winners, key=lambda x: -abs(x["edge_pp"])):
        lines.append(
            f"{r['horizon_h']:>4} {r['left_name']:<26} {r['left_cell']:<5} "
            f"{r['right_name']:<26} {r['right_cell']:<5} {r['n']:>7} "
            f"{r['edge_pp']:>8.2f} {r['mean_ret'] - r['baseline_ret']:>8.4f} "
            f"{_fmt(r.get('lift'))} {_fmt(r.get('oos_edge_pp'))}")
    return "\n".join(lines)


def _fmt(x, blank="       -"):
    return blank if x is None or (isinstance(x, float) and math.isnan(x)) else f"{x:>8.4f}"


def report(conn):
    run_at = conn.execute("SELECT MAX(run_at) FROM pair_measurement").fetchone()[0]
    if run_at is None:
        return "측정한 적이 없다. `python pairs.py` 를 먼저 돌린다."
    out = []
    for era in ERAS:
        rows = [dict(zip([c[0] for c in cur.description], row))
                for cur in [conn.execute(
                    "SELECT * FROM pair_measurement WHERE run_at=? AND era=?", (run_at, era))]
                for row in cur.fetchall()]
        if rows:
            out.append(render(rows, era))
    return "\n\n".join(out) if out else "이 실행에 저장된 줄이 없다."


def main(argv):
    horizons = HORIZONS_HOURS
    eras = ERAS
    if "--horizon" in argv:
        horizons = (int(argv[argv.index("--horizon") + 1]),)
    if "--era" in argv:
        eras = (argv[argv.index("--era") + 1],)
    with store.connect() as conn:
        if "--report" in argv:
            print(report(conn))
            return 0
        run_at = int(time.time() * 1000)
        for era in eras:
            print(f"[{era}] 재는 중…", flush=True)
            rows = run(conn, horizons, era)
            save(conn, rows, run_at)
            print(render(rows, era))
            print()
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
