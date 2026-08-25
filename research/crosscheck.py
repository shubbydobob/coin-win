"""덤프와 REST 가 같은 값을 주는지 겹치는 구간에서 맞댄다.

**백필은 덤프를 보고 수집은 REST 를 본다.** 둘이 다르면 이어지는 지점에 계단이 생기고, 그
계단은 확장 윈도우 백분위에서 실제 신호처럼 보인다. 지표가 급변한 것과 출처가 바뀐 것을
구별할 방법이 분석 단계에는 없다 — 그래서 여기서 미리 묻는다.

겹치는 구간은 최근 31일이다. 그보다 오래된 곳은 REST 가 답하지 않는다.

    python crosscheck.py [--days 3]
"""

import argparse
import datetime as dt
import statistics
import sys
import time

import dumps
import rest
from config import SYMBOL

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

UTC = dt.timezone.utc

#: 상대 오차가 이보다 크면 적어 둔다.
#:
#: **개수가 아니라 크기를 봐야 한다.** 처음에는 1e-6 을 썼는데 그러면 288격자 × 6지표가
#: 거의 전부 "어긋남" 으로 세어지고, 그 숫자는 두 출처가 얼마나 다른지를 하나도 말해 주지
#: 않는다. 분위로 적는 이유가 그것이다.
TOLERANCE = 0.01


def compare(day):
    """하루치를 맞댄다. `(맞댄 격자 수, {지표: 상대오차 목록}, 한쪽만 있는 격자 수)`."""
    dump_rows = dict(dumps.metrics_day(day))
    start = int(dt.datetime.fromisoformat(day).replace(tzinfo=UTC).timestamp() * 1000)
    api_rows = rest.metrics_5m(limit=500, start_ms=start, end_ms=start + 86_400_000 - 1)

    both = sorted(set(dump_rows) & set(api_rows))
    only = len(set(dump_rows) ^ set(api_rows))
    errors = {}
    for ts in both:
        for key, dump_value in dump_rows[ts].items():
            api_value = api_rows[ts].get(key)
            if dump_value is None or api_value is None:
                continue
            scale = max(abs(dump_value), abs(api_value), 1e-12)
            errors.setdefault(key, []).append(abs(dump_value - api_value) / scale)
    return len(both), errors, only


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--days", type=int, default=3, help="며칠을 맞댈 것인가 (T-1 부터 거슬러)")
    args = ap.parse_args(argv)

    today = dt.datetime.now(UTC).date()
    print(f"{SYMBOL} 덤프 vs REST - 5분 격자 파생 통계, 최근 {args.days}일")
    print()

    pooled, grids, only_one = {}, 0, 0
    for back in range(1, args.days + 1):
        day = (today - dt.timedelta(days=back)).isoformat()
        n, errors, only = compare(day)
        grids += n
        only_one += only
        for key, values in errors.items():
            pooled.setdefault(key, []).extend(values)
        time.sleep(1)

    print(f"맞댄 격자 {grids}개, 한쪽에만 있는 격자 {only_one}개")
    print()
    print(f"{'지표':28s} {'표본':>7s} {'중앙값':>10s} {'95%':>10s} {'최대':>10s}")
    worst = 0.0
    for key, values in pooled.items():
        values.sort()
        p95 = values[min(int(len(values) * 0.95), len(values) - 1)]
        worst = max(worst, values[-1])
        print(f"{key:28s} {len(values):7d} "
              f"{statistics.median(values) * 100:9.4f}% {p95 * 100:9.4f}% {values[-1] * 100:9.4f}%")

    print()
    if worst <= TOLERANCE:
        print(f"가장 큰 차이가 {worst * 100:.4f}% 로 기준({TOLERANCE * 100:.1f}%) 안이다.")
    else:
        print(f"가장 큰 차이가 {worst * 100:.4f}% 로 기준({TOLERANCE * 100:.1f}%)을 넘는다.")
    print("두 출처는 같은 값이 아니라 **가까운 값**이다. REST 로 채운 자리는 그 날의 덤프가"
          " 올라오면 백필이 덮어쓴다 - `ingest_log` 에 그 날이 없기 때문이다.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
