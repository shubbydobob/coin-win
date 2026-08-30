"""측정 — 스냅샷의 지표가 라벨에 대해 예측력을 갖는가.

Phase 3. 스냅샷이 "그 시각에 알려져 있던 사실" 이고 라벨이 "그 뒤에 일어난 일" 이면, 이
모듈은 **둘 사이에 관계가 있는지** 를 묻는다. 관계를 만들어 내지 않고 잰다 —
**"예측력 없음" 이 유효한 결론이고 그쪽이 기본 가정이다.**

**판정 기준을 여기서 정하지 않는다.** 넷 다 `config.py` 에 있고 분석보다 먼저 박혀 있다
(`MIN_SAMPLES` · `ALPHA` · `MIN_WINRATE_EDGE_PP` · `LIQUIDATION_DISTANCE_PCT`). 결과를 보고
기준을 고치면 그 순간 이 파이프라인이 재는 것은 시장이 아니라 우리의 기대가 된다.

**기준선은 지표마다 따로 잡는다.** 전체 승률(README 의 마지막 칸)과 비교하고 싶지만, 결측이
지표마다 다른 구간에 몰려 있어서 그렇게 하면 결측이 우위로 둔갑한다 — 상위 계정 비율은
2022년(하락장)의 87%가 비어 있고, 그 지표의 다섯 분위는 전부 "2022년이 덜 섞인 표본" 이다.
그 편향은 다섯 분위에 **같은 방향으로** 실리므로 전체 승률과 대면 다섯 개가 나란히 좋아
보이거나 나란히 나빠 보인다. 그래서 비교 상대는 **그 지표가 값을 가진 행들의 승률**이다.
전체 승률은 표 머리에 함께 찍어 두어 둘의 차이를 볼 수 있게 했다.

**검정은 분위 대 나머지 네 분위다.** 기준선이 분위를 포함하므로 "분위 대 기준선" 은 두
표본이 독립이 아니다. 나머지와 대면 독립이고, 두 우위는 정확히 5/4 배 관계라 판정 기준
(`MIN_WINRATE_EDGE_PP`, 기준선 대비 %p)의 뜻이 흐려지지 않는다.

**부호는 방향이지 강도가 아니다.** 승률이 기준선보다 5%p 낮은 분위는 숏 쪽으로 같은 크기의
우위다(`labels.short_view`). 이 계좌는 양방향으로 거래하므로(`scope.md`) 우위는 크기로 재고
방향은 부호로 적는다. 청산 거리 검사도 그 방향을 따라간다 — 롱 후보는 평균 MAE 를, 숏
후보는 평균 MFE 를 뒤집은 값을 본다.

**결측은 채우지 않는다.** NULL 인 지표는 그 시각을 그 지표에서만 뺀다. 호가 넷은 채움률이
0.04% 라 `MIN_SAMPLES` 에 그냥 걸린다 — 특별 취급하지 않는 것이 요점이다. 규칙이 걸러내야
나중에 다른 지표가 같은 상태가 됐을 때도 같은 답이 나온다.

    python measure.py                # 전부 다시 재고 저장한다
    python measure.py --report       # 재지 않고 마지막 측정 결과만 본다
    python measure.py --horizon 4    # 한 기간만

**`--horizon` 을 쓰면 BH 가족이 그 기간으로 줄어든다.** 검정이 3분의 1이 되므로 같은 줄의
p(BH) 가 전체 실행보다 **작게** 나온다 — 손으로 보정을 느슨하게 한 것과 같다. 확인용으로만
쓰고, 표에 옮겨 적을 값은 인자 없이 돌린 실행에서 가져온다. `--report` 도 마지막 실행을
보므로 `--horizon` 뒤에 부르면 그 기간만 보인다.
"""

import math
import sys
import time

import pandas as pd

import store
from config import (
    ALPHA,
    ERAS,
    ERA_BOUNDARY,
    ROUND_TRIP_COST_PCT,
    CORRELATION_CLUSTER_THRESHOLD,
    EMBARGO_HOURS,
    HORIZONS_HOURS,
    LIQUIDATION_DISTANCE_PCT,
    MIN_SAMPLES,
    MIN_WINRATE_EDGE_PP,
    QUANTILES,
    TRAIN_FRACTION,
)

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

#: 재는 대상. 스냅샷이 가진 지표 전부이고 여기서 새로 만들지 않는다 — 파생 특징(변화율·
#: 이동평균)을 이 자리에서 정의하면 그 정의가 수집기와 갈라진다. `basis` · `basis_rate` 가
#: 이미 저장돼 있는 이유와 같다(`store.DERIVED_COLUMNS`).
SNAPSHOT_INDICATORS = store.METRIC_COLUMNS + store.DERIVED_COLUMNS

#: 판독 지표가 사는 표. 슬롯마다 **그 시각에 이미 닫힌 봉**의 값이 들어 있다
#: (`indicators.py`). 여기서 계산하지 않고 읽기만 한다 — 정의는 자바에만 있다.
INDICATOR_TABLES = {"15m": "indicator_m15", "1h": "indicator_h1", "4h": "indicator_h4"}

#: 분위로 자르지 않는 칸. **값 하나가 한 칸이다.** 구름 위/안/아래를 다섯 분위로 자르면
#: 세 값이 다섯 칸에 흩어지고, 그 표는 지표에 대해 아무 말도 하지 않는다.
CATEGORICAL_BASES = ("cloud_position", "ma_order", "cloud_bullish", "macd_zero_side")

#: 지표 이름 전체. 스냅샷 것과 판독 것이 한 가족으로 보정된다 — 같은 실행에서 같은 라벨을
#: 상대로 재므로 가족을 나눌 이유가 없다.
INDICATORS = list(SNAPSHOT_INDICATORS)

#: 값 자체가 6년 동안 한 방향으로 흐른 지표. 분위가 "높다/낮다" 가 아니라 **"언제였나"** 를
#: 가른다 — 2020년의 가격은 전부 Q1 이고 2025년의 가격은 전부 Q5 다. 그래서 여기서 유의한
#: 결과가 나와도 그것은 신호에 대한 진술이 아니라 시대에 대한 진술일 수 있다.
#: **판정을 바꾸지 않는다.** 기준은 `config.py` 의 넷뿐이고 이것은 읽는 사람에게 붙이는
#: 각주다. 판정을 바꾸는 순간 이것이 다섯 번째 기준이 되고, 그것은 분석 뒤에 기준을 만드는
#: 일이다.
LEVEL_LIKE = frozenset(
    {"perp_price", "spot_price", "open_interest", "open_interest_value", "basis"}
) | frozenset(
    # 판독 지표 중 수준값인 것들. **명세 § 7 이 결과를 보기 전에 꼽아 둔 둘이다** —
    # 밴드폭과 이동평균 간격은 변동성 국면이 통째로 옮겨가면 분위가 "넓다/좁다" 가 아니라
    # "언제였나" 를 가른다. 순위(`bandwidth_rank`)는 창 안에서 다시 재므로 여기 없다.
    f"{base}_{interval}"
    for base in ("bandwidth", "ma_spread_atr")
    for interval in ("15m", "1h", "4h")
)

#: 측정 결과는 **파생이다.** 원천(`store._SCHEMA`)에 섞지 않고 여기서 만든다 — 스냅샷과
#: 라벨은 다시 만들 수 없지만 이 표는 언제든 다시 계산된다. `quantile` 이 -1 이면 분위를
#: 나누기 전에 지표 전체가 표본 미달로 끝난 행이다.
_SCHEMA = """
CREATE TABLE IF NOT EXISTS measurement (
    run_at      INTEGER NOT NULL,
    era         TEXT    NOT NULL,
    horizon_h   INTEGER NOT NULL,
    indicator   TEXT    NOT NULL,
    quantile    INTEGER NOT NULL,
    label       TEXT,
    lo          REAL,
    hi          REAL,
    n           INTEGER NOT NULL,
    winrate     REAL,
    baseline    REAL,
    baseline_ret REAL,
    edge_pp     REAL,
    mean_ret    REAL,
    median_ret  REAL,
    mean_mfe    REAL,
    mean_mae    REAL,
    p_raw       REAL,
    p_bh        REAL,
    cluster     TEXT,
    representative INTEGER,
    oos_n       INTEGER,
    oos_edge_pp REAL,
    verdict     TEXT    NOT NULL,
    PRIMARY KEY (run_at, era, horizon_h, indicator, quantile)
);
"""

MS_PER_HOUR = 3_600_000


# ── 통계 ────────────────────────────────────────────────────────────────────

def two_proportion_p(k1, n1, k2, n2):
    """두 승률이 같다는 가설의 **양측** p. 정규근사 2표본 비율 검정이다.

    `scipy` 를 쓰지 않고 손으로 쓴 이유는 의존성 하나를 아끼려는 것이 아니라 `requirements.txt`
    가 `pandas` · `numpy` 둘뿐이어서다. 정확한 방법은 이렇다.

        p̂ = (k1+k2)/(n1+n2)                       # 귀무가설 아래의 공통 비율
        SE = sqrt(p̂(1-p̂)(1/n1 + 1/n2))
        z  = (k1/n1 - k2/n2) / SE
        p  = 2(1 - Φ(|z|)) = erfc(|z| / √2)

    연속성 보정은 넣지 않았다. `MIN_SAMPLES` 가 1,000 이라 두 표본 모두 그보다 크고, 그
    크기에서 보정은 p 를 셋째 자리 아래에서만 움직인다 — `ALPHA` 근처의 판정을 뒤집지 않는다.

    **근사가 성립하는 조건은 `n·p̂ ≥ 5` 와 `n(1-p̂) ≥ 5` 이고**, 승률이 50% 언저리이고 표본이
    1,000 이상이므로 여기서는 항상 만족한다. 그 조건이 깨질 만큼 표본이 작으면 그 분위는
    이미 "데이터 부족" 으로 끝나 이 함수까지 오지 않는다.
    """
    if n1 <= 0 or n2 <= 0:
        return 1.0
    pooled = (k1 + k2) / (n1 + n2)
    se = math.sqrt(pooled * (1.0 - pooled) * (1.0 / n1 + 1.0 / n2))
    if se == 0.0:
        return 1.0
    z = (k1 / n1 - k2 / n2) / se
    return math.erfc(abs(z) / math.sqrt(2.0))


def benjamini_hochberg(pvalues):
    """BH 보정된 q 값. 순서는 입력 그대로 돌려준다.

    **왜 필요한가.** 지표 15 × 분위 5 × 기간 3 이면 검정이 200개를 넘는다. 아무 관계가 없어도
    5% 는 p<0.05 를 낸다 — 열 개 넘는 "유의한 발견" 이 순전히 우연으로 나온다는 뜻이다.

    Bonferroni 가 아니라 BH 인 이유는 **검정들이 서로 독립이 아니기 때문**이다. 같은 지표의
    다섯 분위는 한 표본을 나눈 것이고 세 기간은 겹치는 미래를 본다. Bonferroni 는 그 상황에서
    지나치게 보수적이라 있는 관계도 못 본다. BH 는 위양성 개수가 아니라 **비율**(FDR)을
    통제하고, 그것이 "몇 개를 더 들여다볼 것인가" 라는 이 단계의 질문에 맞는다.

    한 가족으로 묶는 범위는 **한 번의 실행 전체**다. 기간마다 따로 보정하면 세 가족이 되고
    같은 데이터를 세 번 훑는 대가가 사라진다.
    """
    m = len(pvalues)
    if m == 0:
        return []
    order = sorted(range(m), key=lambda i: pvalues[i])
    q = [1.0] * m
    running = 1.0
    for rank in range(m, 0, -1):          # 큰 p 부터 내려오며 단조성을 강제한다
        i = order[rank - 1]
        running = min(running, pvalues[i] * m / rank)
        q[i] = min(running, 1.0)
    return q


def correlation_clusters(frame, names, threshold=CORRELATION_CLUSTER_THRESHOLD):
    """상관이 임계 이상인 지표들을 한 덩이로 묶는다. `{지표: 클러스터 이름}` 을 돌려준다.

    **순위 상관(스피어만)을 쓴다.** 분석 자체가 분위로 하는 것이라 관심사가 "같은 순서로
    움직이는가" 이고, 피어슨은 6년 동안 함께 자란 수준값 두 개를 곡선 모양이 달라도 1 에
    가깝게 본다. 순위로 재면 그 부풀림이 줄어든다.

    부호는 버린다 — 완전히 반대로 움직이는 두 지표는 같은 사실을 두 번 말하는 것이다.

    묶는 방법은 단일 연결(single linkage)이다: A-B 가 붙고 B-C 가 붙으면 A-C 의 상관이
    임계 아래여도 한 덩이가 된다. **느슨한 쪽으로 틀리는 선택**이고 의도한 것이다 — 독립
    증거를 실제보다 적게 세는 오류가 많게 세는 오류보다 싸다.
    """
    # **범주형은 상관을 잴 수 없다.** 순위 상관은 순서가 있는 값에만 뜻이 있고, 구름 위/안/
    # 아래에는 순서가 없다. 그래서 각자 한 덩이로 둔다 — 묶이지 않는 것과 클러스터가 없는
    # 것은 다르다. 클러스터가 없으면 대표로도 뽑히지 않아 **독립 증거에서 조용히 사라진다.**
    counted = [c for c in names if frame[c].notna().sum() >= MIN_SAMPLES]
    usable = [c for c in counted if not is_categorical(c)]
    parent = {c: c for c in counted}

    def find(x):
        while parent[x] != x:
            parent[x] = parent[parent[x]]
            x = parent[x]
        return x

    if len(usable) > 1:
        rho = frame[usable].corr(method="spearman")
        for i, a in enumerate(usable):
            for b in usable[i + 1:]:
                value = rho.at[a, b]
                if pd.notna(value) and abs(value) >= threshold:
                    parent[find(a)] = find(b)

    groups = {}
    for c in counted:
        groups.setdefault(find(c), []).append(c)
    # 클러스터 이름은 그 안에서 가장 이른 이름이다. 실행마다 같은 이름이 나와야 표를 맞댈 수
    # 있고, 딕셔너리 순서에 기대면 그것이 성립하지 않는다.
    return {c: min(members) for members in groups.values() for c in members}


def representatives(clusters, counts):
    """클러스터마다 대표 하나. 표본이 가장 많은 지표이고, 같으면 이름이 이른 쪽이다.

    표본 수로 고르는 이유는 **증거가 가장 많은 쪽이 그 덩이의 이야기를 가장 잘 대표하기**
    때문이다. 우위가 큰 것을 고르면 클러스터마다 최고를 집는 셈이 되고, 그것은 측정이 아니라
    고르기다(`docs/adr/021` 이 감도표에 대해 적어 둔 것과 같은 함정이다).
    """
    best = {}
    for name, cluster in clusters.items():
        key = (-counts.get(name, 0), name)
        if cluster not in best or key < best[cluster][0]:
            best[cluster] = (key, name)
    return {name for _, name in best.values()}


# ── 한 지표를 재는 일 ────────────────────────────────────────────────────────

def _bins(values, quantiles=QUANTILES):
    """분위 코드와 경계. 같은 값이 경계에 걸려 분위가 줄면 줄어든 채로 돌려준다.

    `duplicates="drop"` 을 쓰는 이유는 값이 같은 두 행을 다른 분위에 넣지 않기 위해서다.
    순위로 자르면 다섯 칸이 항상 채워지지만, 그러면 **똑같은 지표값이 어떤 행은 Q2 어떤 행은
    Q3** 이 된다 — 그 표는 지표에 대해 아무 말도 하지 않는다. 칸이 줄어든 것은 그 지표가
    그만큼 뭉쳐 있다는 사실이고, 표에 그대로 드러나는 편이 낫다.
    """
    codes, edges = pd.qcut(values, quantiles, labels=False, retbins=True, duplicates="drop")
    return codes, edges


def _adverse_pct(edge_pp, mean_mfe_pct, mean_mae_pct):
    """이 분위를 신호로 썼을 때 **반대로 간 평균 거리**(%).

    롱 후보(우위가 양수)는 롱 기준 MAE 그대로이고, 숏 후보(우위가 음수)는 롱 기준 MFE 를
    뒤집은 것이다 — 가격이 올라간 거리가 숏에게는 손실이다(`labels.short_view`).
    라벨을 롱 한 벌만 저장한 결정이 여기서 값을 한다: 정의가 한 곳에 있다.
    """
    return mean_mae_pct if edge_pp >= 0 else -mean_mfe_pct


def _cells(values, name, quantiles=QUANTILES):
    """칸을 나눈다. `(칸 코드, 칸 수, 칸 이름 함수)`.

    **범주형은 값 하나가 한 칸이다.** 구름 위/안/아래를 다섯 분위로 자르면 세 값이 다섯 칸에
    흩어지고, 그 표는 지표에 대해 아무 말도 하지 않는다.
    """
    if is_categorical(name):
        labels = sorted(values.dropna().unique().tolist(), key=str)
        codes = values.map({label: i for i, label in enumerate(labels)})
        return codes, len(labels), (lambda q: (str(labels[q]), None, None))
    codes, edges = _bins(values, quantiles)
    return codes, len(edges) - 1, (lambda q: (None, float(edges[q]), float(edges[q + 1])))


def measure_indicator(frame, name, quantiles=QUANTILES):
    """한 지표를 칸으로 갈라 칸마다 한 줄. p 는 아직 보정 전이고 판정도 아직 없다."""
    sub = frame[[name, "ret", "mfe", "mae"]].dropna(subset=[name])
    total = len(sub)
    if total < MIN_SAMPLES:
        # 지표 전체가 표본 미달이면 칸을 나누지 않는다. 나누면 여러 줄이 생기고, 여러 줄은
        # 아무것도 모른다는 사실보다 더 많이 아는 것처럼 보인다.
        return [{"indicator": name, "quantile": -1, "n": total, "p_raw": None}]

    codes, cells, describe = _cells(sub[name], name, quantiles)
    wins = sub["ret"] > 0
    total_wins = int(wins.sum())
    baseline = total_wins / total
    baseline_ret = sub["ret"].mean() * 100

    rows = []
    for q in range(cells):
        mask = codes == q
        n1 = int(mask.sum())
        k1 = int(wins[mask].sum())
        n2, k2 = total - n1, total_wins - k1
        winrate = k1 / n1 if n1 else float("nan")
        label, lo, hi = describe(q)
        rows.append({
            "indicator": name,
            "quantile": q,
            "label": label,
            "lo": lo,
            "hi": hi,
            "baseline_ret": baseline_ret,
            "n": n1,
            "winrate": winrate,
            "baseline": baseline,
            "edge_pp": (winrate - baseline) * 100 if n1 else float("nan"),
            "mean_ret": sub.loc[mask, "ret"].mean() * 100,
            "median_ret": sub.loc[mask, "ret"].median() * 100,
            "mean_mfe": sub.loc[mask, "mfe"].mean() * 100,
            "mean_mae": sub.loc[mask, "mae"].mean() * 100,
            # 표본 미달 분위는 검정 자체를 하지 않는다. p 를 내면 그 값이 BH 가족에 끼어
            # 다른 검정의 문턱을 움직인다 — 결론을 내지 않기로 한 줄이 남의 결론을 바꾸는 꼴이다.
            "p_raw": two_proportion_p(k1, n1, k2, n2) if n1 >= MIN_SAMPLES else None,
        })
    return rows


def out_of_sample(frame, name, quantiles=QUANTILES):
    """분위 경계를 앞 구간에서만 얻어 뒤 구간에 씌운다. `{분위: (표본, 우위 %p)}`.

    **이것은 판정이 아니다.** `config.py` 의 넷은 여전히 전체 구간에서 나오고, 이 칸은 그
    옆에 붙는 각주다. 그런데도 재는 이유는 `docs/adr/021` 이 워크포워드로 확인한 것 때문이다 —
    한 구간에서 여럿을 훑어 좋은 것을 집으면 그 구간에서는 정의상 언제나 좋다.

    경계 사이를 `EMBARGO_HOURS` 만큼 비운다. 학습 구간 마지막 슬롯의 24시간 라벨은 검증 구간
    첫 슬롯들의 **특징과 같은 시간**을 보므로, 비우지 않으면 두 구간이 겹친다.
    """
    sub = frame[["slot_ts", name, "ret"]].dropna(subset=[name]).sort_values("slot_ts")
    if len(sub) < MIN_SAMPLES:
        return {}
    cutoff = sub["slot_ts"].iloc[int(len(sub) * TRAIN_FRACTION)]
    train = sub[sub["slot_ts"] < cutoff]
    valid = sub[sub["slot_ts"] >= cutoff + EMBARGO_HOURS * MS_PER_HOUR]
    if len(train) < MIN_SAMPLES or len(valid) < MIN_SAMPLES:
        return {}

    if is_categorical(name):
        # **범주는 고를 것이 없다.** 칸이 값 자체라 학습 구간에서 경계를 얻는 단계가 없고,
        # 그래서 이 칸이 묻는 것은 "고른 경계가 밖에서도 서는가" 가 아니라 "관계가 뒤 구간에서도
        # 같은 방향인가" 다. 칸 번호는 구간 안 표와 **같은 순서**여야 하므로 전 구간에서 얻는다.
        labels = sorted(sub[name].dropna().unique().tolist(), key=str)
        codes = valid[name].map({label: i for i, label in enumerate(labels)})
        cells = len(labels)
    else:
        _, edges = _bins(train[name], quantiles)
        edges = [-math.inf, *list(edges[1:-1]), math.inf]  # 밖으로 벗어난 값도 양 끝에 담는다
        codes = pd.cut(valid[name], edges, labels=False, duplicates="drop")
        cells = len(edges) - 1
    wins = valid["ret"] > 0
    baseline = wins.mean()
    out = {}
    for q in range(cells):
        mask = codes == q
        n = int(mask.sum())
        # 검증 구간의 그 칸이 `MIN_SAMPLES` 를 못 채우면 수를 내지 않는다. 여기서 이 규칙이
        # 실제로 발동하는 자리는 **수준값**이다 — 미결제약정의 학습 구간 Q1 은 2020~2022년의
        # 낮은 값이고, 2025년에는 그 구간에 들어오는 슬롯이 거의 없다. 그렇게 만들어진 몇십
        # 행짜리 우위가 표에서 가장 커 보이는 수가 되는 것이 정확히 이 규칙이 막는 일이다.
        edge = float("nan")
        if n >= MIN_SAMPLES:
            edge = (wins[mask].mean() - baseline) * 100
        out[q] = (n, edge)
    return out


def verdict(row):
    """`config.py` 의 다섯 기준을 순서대로 적용한다. 여기서 새 기준을 만들지 않는다.

    순서가 뜻을 갖는다. **표본 미달이 가장 먼저**인 것은 "아무 결론도 내지 않는다" 가 다른
    어떤 판정보다 강하기 때문이고, **청산 거리가 그다음**인 것은 그 기준이 "승률과 무관하게"
    라고 적혀 있기 때문이다.

    **비용이 가장 마지막이다.** 승률 기준을 통과하고도 금액에서 지는 자리가 실제로 있었고
    (`stability.py`), 그때 드러난 것은 기준이 틀렸다는 것이 아니라 **기준이 승률로만 적혀
    있었다는 사실**이었다. 기준선을 뺀 뒤 왕복 비용을 넘는지 본다 — 빼지 않으면 시장이
    올라서 생긴 수익을 신호의 값으로 읽는다.

    **이 기준은 진입 칸의 것이다.** 손절·사이징은 거래를 새로 만들지 않으므로 비용을 넘을
    이유가 없고, 그 칸의 답은 판정이 아니라 MAE 분포에서 읽는다
    (`docs/spec/indicator-usage.md` § 3).
    """
    if row["n"] < MIN_SAMPLES:
        return "데이터 부족"
    if _adverse_pct(row["edge_pp"], row["mean_mfe"], row["mean_mae"]) < LIQUIDATION_DISTANCE_PCT:
        return "사용 불가"
    if row.get("p_bh") is None or row["p_bh"] >= ALPHA:
        return "예측력 없음"
    if abs(row["edge_pp"]) < MIN_WINRATE_EDGE_PP:
        return "실용적으로 무의미"
    if abs(row["mean_ret"] - row.get("baseline_ret", 0.0)) < ROUND_TRIP_COST_PCT:
        return "비용을 못 넘는다"
    return "유효 후보"


# ── 실행 ────────────────────────────────────────────────────────────────────

def readout_columns(conn):
    """붙어 있는 판독 지표 칸. `{프레임 이름: (표, 원래 칸)}`.

    **표에서 읽는다.** 여기 목록을 손으로 적으면 `docs/spec/indicator-usage.md` § 4 가 늘 때
    한쪽만 낡는다 — 자바가 CSV 에 낸 칸이 그대로 표가 되고 표가 그대로 여기 온다.
    """
    out = {}
    for interval, table in INDICATOR_TABLES.items():
        rows = conn.execute(
            "SELECT name FROM sqlite_master WHERE type='table' AND name=?", (table,)
        ).fetchall()
        if not rows:
            continue
        for info in conn.execute(f"PRAGMA table_info({table})"):
            name = info[1]
            if name not in ("slot_ts", "bar_open_time", "bar_close_time"):
                out[f"{name}_{interval}"] = (table, name)
    return out


def is_categorical(name):
    """분위로 자르지 않는 칸인가. 이름은 `{칸}_{주기}` 다."""
    return any(name.startswith(base) for base in CATEGORICAL_BASES)


def era_bounds(era):
    """`(시작, 끝)` 밀리초. `None` 은 열려 있다는 뜻이다."""
    if era == "all":
        return None, None
    boundary = int(pd.Timestamp(ERA_BOUNDARY, tz="UTC").timestamp() * 1000)
    return (None, boundary) if era == "reversion" else (boundary, None)


def load(conn, horizon, readout, era="all"):
    """스냅샷·판독 지표·라벨을 슬롯으로 맞댄다. 라벨이 없는 슬롯은 빠진다."""
    selects = ["s.slot_ts"] + [f"s.{c}" for c in SNAPSHOT_INDICATORS]
    joins = []
    for index, (interval, table) in enumerate(INDICATOR_TABLES.items()):
        names = [name for frame_name, (owner, name) in readout.items() if owner == table]
        if not names:
            continue
        alias = f"t{index}"
        selects += [f"{alias}.{name} AS {name}_{interval}" for name in names]
        joins.append(f"LEFT JOIN {table} {alias} ON {alias}.slot_ts = s.slot_ts")
    snap = pd.read_sql_query(
        f"SELECT {', '.join(selects)} FROM snapshot s {' '.join(joins)} ORDER BY s.slot_ts", conn
    )
    label = pd.read_sql_query(
        "SELECT slot_ts, ret, mfe, mae FROM label WHERE horizon_h=?", conn, params=(horizon,)
    )
    frame = snap.merge(label, on="slot_ts", how="inner")
    start, end = era_bounds(era)
    if start is not None:
        frame = frame[frame["slot_ts"] >= start]
    if end is not None:
        frame = frame[frame["slot_ts"] < end]
    return frame.reset_index(drop=True)


def run(conn, horizons=HORIZONS_HOURS, era="all"):
    """한 국면·전 지표를 재고 BH 로 보정한 뒤 판정을 붙인다.

    **BH 가족은 한 국면 안이다.** 국면을 나눠 재는 것은 세 개의 다른 질문이므로 한 실행에
    묶지 않는다 — 묶으면 "2024년 이후에 이런 관계가 있었나" 의 문턱이 2020년 표에 달린다.
    """
    readout = readout_columns(conn)
    INDICATORS[:] = SNAPSHOT_INDICATORS + sorted(readout)
    rows = []
    frames = {}
    for hours in horizons:
        frame = load(conn, hours, readout, era)
        frames[hours] = frame
        oos = {name: out_of_sample(frame, name) for name in INDICATORS}
        counts = {name: int(frame[name].notna().sum()) for name in INDICATORS}
        clusters = correlation_clusters(frame, INDICATORS)
        reps = representatives(clusters, counts)
        for name in INDICATORS:
            for row in measure_indicator(frame, name):
                row["horizon_h"] = hours
                row["era"] = era
                row["indicator_n"] = counts[name]
                row["cluster"] = clusters.get(name)
                row["representative"] = name in reps
                row["oos_n"], row["oos_edge_pp"] = oos.get(name, {}).get(
                    row["quantile"], (None, None)
                )
                rows.append(row)

    tested = [r for r in rows if r.get("p_raw") is not None]
    for row, q in zip(tested, benjamini_hochberg([r["p_raw"] for r in tested])):
        row["p_bh"] = q
    for row in rows:
        row.setdefault("p_bh", None)
        row["verdict"] = verdict(row)
    return rows, frames


def _migrate(conn):
    """모양이 달라진 낡은 표는 버린다.

    **`measurement` 는 파생이다.** 스냅샷과 라벨은 다시 만들 수 없지만 이 표는 언제든 다시
    계산된다 — 칸이 늘 때 옮겨 담는 코드를 쓰는 것은 다시 계산하는 것보다 비싸고, 그 코드가
    틀리면 옛 판정과 새 판정이 한 표에 섞인다.
    """
    existing = {row[1] for row in conn.execute("PRAGMA table_info(measurement)")}
    if existing and not {"era", "label", "baseline_ret"} <= existing:
        conn.execute("DROP TABLE measurement")


def save(conn, rows, run_at):
    _migrate(conn)
    conn.executescript(_SCHEMA)
    conn.executemany(
        "INSERT INTO measurement (run_at, era, horizon_h, indicator, quantile, label, lo, hi, "
        "n, winrate, baseline, baseline_ret, edge_pp, mean_ret, median_ret, mean_mfe, mean_mae, "
        "p_raw, p_bh, cluster, representative, oos_n, oos_edge_pp, verdict) "
        "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) "
        "ON CONFLICT DO NOTHING",
        [
            (
                run_at, r.get("era", "all"), r["horizon_h"], r["indicator"], r["quantile"],
                r.get("label"), r.get("lo"), r.get("hi"), r["n"], r.get("winrate"),
                r.get("baseline"), r.get("baseline_ret"),
                r.get("edge_pp"), r.get("mean_ret"), r.get("median_ret"),
                r.get("mean_mfe"), r.get("mean_mae"), r.get("p_raw"), r.get("p_bh"),
                r.get("cluster"), int(bool(r.get("representative"))),
                r.get("oos_n"), r.get("oos_edge_pp"), r["verdict"],
            )
            for r in rows
        ],
    )
    conn.commit()


# ── 표 ──────────────────────────────────────────────────────────────────────

_HEAD = ("분위  구간                          표본     승률    Δ%p   평균수익률   중앙값"
         "    평균MFE   평균MAE   p(BH)     구간밖   판정")


def _fmt(x, spec, blank="-"):
    return blank if x is None or (isinstance(x, float) and math.isnan(x)) else format(x, spec)


def render(rows, frames=None, era="all"):
    """콘솔 표. 기간 → 지표 → 칸 순서로 찍는다."""
    print()
    note = "" if era == "all" else (
        f"  (경계 {ERA_BOUNDARY} — stability.py 가 테이커 비율에서 찾은 날이다. "
        "지표마다 다시 고르지 않는다)")
    print(f"국면 {era}" + note)
    for hours in sorted({r["horizon_h"] for r in rows}):
        block = [r for r in rows if r["horizon_h"] == hours]
        head = f"\n{'═' * 132}\n{hours}시간"
        if frames is not None and hours in frames:
            frame = frames[hours]
            up = (frame["ret"] > 0).mean() * 100
            head += f"  라벨 {len(frame):,}행  전체 승률 {up:.2f}%"
        print(head + f"\n{'═' * 132}")
        for name in sorted({r["indicator"] for r in block}, key=INDICATORS.index):
            lines = [r for r in block if r["indicator"] == name]
            if lines:
                _render_indicator(name, lines)
        _render_summary(block)


def _render_indicator(name, lines):
    first = lines[0]
    tags = []
    if first.get("cluster") and first["cluster"] != name:
        tags.append(f"클러스터 {first['cluster']}")
    if first.get("cluster") and not first.get("representative"):
        tags.append("중복 증거")
    if name in LEVEL_LIKE:
        tags.append("수준값 — 분위가 시대를 가른다")
    suffix = f"  ({', '.join(tags)})" if tags else ""
    print(f"\n{name}  표본 {first.get('indicator_n', first['n']):,}{suffix}")
    if lines[0]["quantile"] == -1:
        print(f"  표본 {first['n']:,} < {MIN_SAMPLES:,} — 데이터 부족. 분위를 나누지 않는다.")
        return
    print("  " + _HEAD)
    for r in lines:
        span = (f"{r['label']:>29}" if r.get("label")
                else f"[{_fmt(r.get('lo'), '>12,.4f')} ~ {_fmt(r.get('hi'), '>12,.4f')}]")
        winrate = r.get("winrate")
        print(
            f"  Q{r['quantile'] + 1}    {span}  {r['n']:>7,}  "
            f"{_fmt(None if winrate is None else winrate * 100, '>5.2f')}%  "
            f"{_fmt(r.get('edge_pp'), '>+5.2f')}  {_fmt(r.get('mean_ret'), '>+9.4f')}%  "
            f"{_fmt(r.get('median_ret'), '>+7.4f')}%  {_fmt(r.get('mean_mfe'), '>+7.3f')}%  "
            f"{_fmt(r.get('mean_mae'), '>+7.3f')}%  {_fmt(r.get('p_bh'), '>7.4f')}  "
            f"{_fmt(r.get('oos_edge_pp'), '>+6.2f')}  {r['verdict']}"
        )


def _render_summary(block):
    tally = {}
    for r in block:
        tally[r["verdict"]] = tally.get(r["verdict"], 0) + 1
    order = ["유효 후보", "비용을 못 넘는다", "실용적으로 무의미", "예측력 없음",
             "사용 불가", "데이터 부족"]
    print("\n  판정(줄 수, 중복 증거 포함): " + " · ".join(
        f"{k} {tally[k]}" for k in order if k in tally
    ))
    live = [r for r in block if r["verdict"] == "유효 후보"]
    # 클러스터 대표만 독립 증거로 센다. 대표가 아닌 줄도 지우지 않고 그대로 적는다 — 같은
    # 사실을 두 번 말하고 있다는 것이 보여야 두 번 세지 않았다는 것도 보인다.
    reps = [r for r in live if r.get("representative")]
    for r in live:
        tag = "" if r.get("representative") else "  (중복 증거 — 독립으로 세지 않는다)"
        oos = r.get("oos_edge_pp")
        # 검증 구간의 그 칸이 `MIN_SAMPLES` 를 못 채웠다는 뜻이다. "0" 도 "-" 도 아니고
        # **잴 수 없었다** 이므로 그렇게 적는다 - 비어 있는 것과 알 수 없는 것은 다르다.
        shown = "잴 수 없음" if oos is None or math.isnan(oos) else f"{oos:+.2f}%p"
        print(f"    유효 후보  {r['indicator']} Q{r['quantile'] + 1}  "
              f"우위 {r['edge_pp']:+.2f}%p  p(BH) {r['p_bh']:.4f}  구간밖 {shown}{tag}")
    print(f"    독립 증거로 셀 수 있는 유효 후보 {len(reps)}개.")


def report(conn):
    """마지막 실행 결과만 읽어 찍는다. 다시 재지 않는다."""
    _migrate(conn)
    conn.executescript(_SCHEMA)
    last = conn.execute("SELECT MAX(run_at) FROM measurement").fetchone()[0]
    if last is None:
        print("측정 결과가 없다. `python measure.py` 를 먼저 돌린다.")
        return
    cur = conn.execute("SELECT * FROM measurement WHERE run_at=?", (last,))
    cols = [d[0] for d in cur.description]
    rows = [dict(zip(cols, r)) for r in cur.fetchall()]
    # 지표 전체의 표본 수는 저장하지 않는다 — 분위 표본의 합이 곧 그것이라 두 곳에 두면
    # 갈라질 수 있다. 읽는 쪽에서 더한다.
    totals = {}
    for r in rows:
        key = (r["horizon_h"], r["indicator"])
        totals[key] = totals.get(key, 0) + r["n"]
    for r in rows:
        r["representative"] = bool(r["representative"])
        r["indicator_n"] = totals[(r["horizon_h"], r["indicator"])]
    stamp = time.strftime("%Y-%m-%d %H:%M", time.localtime(last / 1000))
    era = rows[0]["era"] if rows else "all"
    print(f"마지막 측정 {stamp}  {len(rows):,}줄")
    render(rows, era=era)


def main(argv):
    horizons = HORIZONS_HOURS
    if "--horizon" in argv:
        horizons = (int(argv[argv.index("--horizon") + 1]),)
    era = argv[argv.index("--era") + 1] if "--era" in argv else "all"
    if era not in ERAS:
        print(f"국면은 {' · '.join(ERAS)} 중 하나다")
        return 2
    with store.connect() as conn:
        store.init(conn)
        if "--report" in argv:
            report(conn)
            return 0
        started = time.time()
        rows, frames = run(conn, horizons, era)
        run_at = int(time.time() * 1000)
        save(conn, rows, run_at)
        render(rows, frames, era)
        print(f"\n{time.time() - started:.1f}초  ·  {len(rows):,}줄 저장")
        print("\n이 표는 신호를 만들지 않는다. 분포를 잰 것이고, "
              "'예측력 없음' 은 실패가 아니라 결과다.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
