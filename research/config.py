"""연구 파이프라인의 상수.

**판정 기준을 여기 박아 두는 이유.** 분석을 돌린 뒤에 기준을 정하면 결과에 맞춰 기준이
움직인다. Phase 3 이 아직 없어도 지금 적어 두는 것이 그 때문이다 — 나중에 이 값을 바꾸려면
왜 바꾸는지를 커밋 메시지에 적어야 하고, 그러면 적어도 바뀐 사실이 남는다.
"""

from pathlib import Path

# ── 대상 ────────────────────────────────────────────────────────────────────
SYMBOL = "BTCUSDT"

#: 스냅샷 격자. 15분이고 UTC 기준 정각에 맞춘다.
SLOT_MINUTES = 15
SLOT_MS = SLOT_MINUTES * 60 * 1000

#: 파생 통계 덤프가 시작되는 날. Phase 0 실측 — 2020-08-31 은 404, 2020-09-01 은 200.
METRICS_FIRST_DAY = "2020-09-01"

#: 선물 1분봉 월별 덤프가 시작되는 달. 2019-09~12 는 404 이고 REST 로만 얻는다.
PERP_KLINE_FIRST_MONTH = "2020-01"

#: 현물 1분봉 월별 덤프가 시작되는 달.
SPOT_KLINE_FIRST_MONTH = "2020-01"

#: 백필의 시작. 모든 지표가 함께 존재하는 가장 이른 날이다.
BACKFILL_START = METRICS_FIRST_DAY

# ── 출처 ────────────────────────────────────────────────────────────────────
DUMP_BASE = "https://data.binance.vision/data"
FAPI_BASE = "https://fapi.binance.com"
SPOT_BASE = "https://api.binance.com"

#: 덤프는 T-1 까지만 올라온다. 그 뒤는 REST 로 메운다.
DUMP_LAG_DAYS = 1

#: `futures/data` 계열은 가중치 헤더를 주지 않아 소비량이 보이지 않는다. Phase 0 조사 중
#: 실제로 -1003 IP 차단을 맞았다. 그래서 이 계열은 백필에 쓰지 않고, 쓸 때도 간격을 둔다.
FUTURES_DATA_MIN_INTERVAL_S = 2.0

#: 덤프 서버에 대한 간격. 문서화된 한도가 없어 보수적으로 둔다.
DUMP_MIN_INTERVAL_S = 0.15

# ── 저장 ────────────────────────────────────────────────────────────────────
DATA_DIR = Path(__file__).parent / "data"
DB_PATH = DATA_DIR / "research.sqlite"

# ── 판정 기준 (Phase 3 에서 쓴다. 분석 전에 정해 둔다) ────────────────────────
#: 이보다 표본이 적으면 어떤 결론도 내지 않는다. "데이터 부족" 한 줄만 찍는다.
MIN_SAMPLES = 1000

#: BH 보정 후 이 값 이상이면 예측력 없음으로 확정한다. "가능성 있음" 은 쓰지 않는다.
ALPHA = 0.05

#: 승률이 기준선 대비 이만큼(%p) 못 넘으면 통계적으로 유의해도 실용적으로 무의미다.
#: 수수료와 슬리피지가 그 이상 먹는다.
MIN_WINRATE_EDGE_PP = 3.0

#: 상관이 이 이상인 지표들은 독립 증거로 세지 않는다. 클러스터당 대표 하나만 센다.
CORRELATION_CLUSTER_THRESHOLD = 0.7

#: 라벨이 +24시간까지 보므로 학습/검증 경계에 이만큼을 비운다.
EMBARGO_HOURS = 24

#: 분위 수.
QUANTILES = 5

#: 라벨 기간.
HORIZONS_HOURS = (1, 4, 24)

#: **자리표시자다.** 실제 청산 거리는 레버리지와 증거금에서 나오고 그 계산은 자바 쪽
#: `MaintenanceMarginPolicy` 가 갖고 있다. 10배 기준 대략치를 넣어 두었을 뿐이며,
#: Phase 3 을 돌리기 전에 실제 값으로 바꿔야 한다. 슬리피지 기본값과 같은 자리다.
LIQUIDATION_DISTANCE_PCT = -8.0
