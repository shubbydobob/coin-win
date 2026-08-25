"""공개 덤프(`data.binance.vision`)에서 읽는다.

**여기가 백필의 유일한 출처다.** REST 파생 통계는 31일만 주고, `futures/data` 계열은 가중치
헤더가 없어 소비량이 보이지 않으며 실제로 IP 차단을 맞았다(Phase 0). 덤프는 그 둘을 모두
피한다.

**형식이 파일마다 다르다. 셋을 정규화한다.**

1. **헤더가 있는 파일과 없는 파일이 섞여 있다.** 2020-01 선물 1분봉은 헤더가 없고 2026-07 은
   있다. 첫 칸이 숫자인지로 가른다.
2. **현물 덤프는 마이크로초, 선물 덤프는 밀리초다.** 2026-07 현물 1분봉의 `open_time` 이
   `1782864000000000`(16자리)이고 선물은 `1782864000000`(13자리)이다. 그대로 넣으면 현물
   캔들이 서기 5만 년에 놓이는데 **그 값은 오류를 내지 않는다** — 조인이 조용히 전부 비는
   것으로만 드러난다. 자릿수로 판별해 밀리초로 맞춘다.
3. **펀딩 덤프의 칸 이름이 REST 와 다르다.** `calc_time,funding_interval_hours,
   last_funding_rate` 이고 REST 는 `fundingTime,fundingRate` 다.
"""

import calendar
import csv
import io
import time
import urllib.error
import urllib.request
import zipfile

from config import DUMP_BASE, DUMP_MIN_INTERVAL_S, SYMBOL

_last_call = [0.0]

#: 이보다 크면 마이크로초다. 밀리초로 읽은 어떤 최근 시각도 이 값을 넘지 않는다.
_MICROS_THRESHOLD = 10**15


def to_millis(raw):
    """마이크로초로 온 시각을 밀리초로 맞춘다. 밀리초는 그대로 둔다."""
    v = int(raw)
    return v // 1000 if v >= _MICROS_THRESHOLD else v


def _throttle():
    gap = time.monotonic() - _last_call[0]
    if gap < DUMP_MIN_INTERVAL_S:
        time.sleep(DUMP_MIN_INTERVAL_S - gap)
    _last_call[0] = time.monotonic()


class DumpMissing(Exception):
    """그 날짜의 덤프가 없다. 정상인 경우가 있다 — 아직 안 올라온 오늘이 그렇다."""


def _read_csv(url):
    _throttle()
    try:
        with urllib.request.urlopen(url, timeout=180) as r:
            raw = r.read()
    except urllib.error.HTTPError as e:
        if e.code == 404:
            raise DumpMissing(url) from e
        raise
    with zipfile.ZipFile(io.BytesIO(raw)) as z:
        text = z.read(z.namelist()[0]).decode()
    rows = list(csv.reader(io.StringIO(text)))
    if rows and not _is_number(rows[0][0]):
        rows = rows[1:]
    return rows


def _is_number(s):
    try:
        float(s)
        return True
    except ValueError:
        return False


def _utc_millis(text):
    """`2025-01-15 00:05:00` 을 UTC 로 읽는다. 지역 시간대에 기대지 않는다."""
    return calendar.timegm(time.strptime(text, "%Y-%m-%d %H:%M:%S")) * 1000


def metrics_day(day):
    """5분 간격 파생 통계 하루치. `(create_time_ms, {지표: 값})` 목록."""
    url = f"{DUMP_BASE}/futures/um/daily/metrics/{SYMBOL}/{SYMBOL}-metrics-{day}.zip"
    out = []
    for r in _read_csv(url):
        if len(r) < 8:
            continue
        out.append((_utc_millis(r[0]), {
            "open_interest": _num(r[2]),
            "open_interest_value": _num(r[3]),
            "top_trader_account_ratio": _num(r[4]),
            "top_trader_position_ratio": _num(r[5]),
            "long_short_account_ratio": _num(r[6]),
            "taker_buy_sell_ratio": _num(r[7]),
        }))
    return out


def _num(s):
    s = s.strip()
    return float(s) if s and s.lower() not in ("nan", "null") else None


def klines(market, interval, period, daily=False):
    """캔들 한 덩이. `market` 은 `perp`/`spot`, `period` 는 `YYYY-MM` 또는 `YYYY-MM-DD`.

    행 순서는 바이낸스 REST 배열과 같고 **시각은 밀리초로 정규화돼 있다.**
    """
    root = "futures/um" if market == "perp" else "spot"
    grain = "daily" if daily else "monthly"
    url = (f"{DUMP_BASE}/{root}/{grain}/klines/{SYMBOL}/{interval}/"
           f"{SYMBOL}-{interval}-{period}.zip")
    out = []
    for r in _read_csv(url):
        if len(r) < 11:
            continue
        row = list(r)
        row[0] = to_millis(row[0])
        row[6] = to_millis(row[6])
        out.append(row)
    return out


def funding_month(month):
    """펀딩비 한 달. `(funding_time_ms, rate)` 목록."""
    url = f"{DUMP_BASE}/futures/um/monthly/fundingRate/{SYMBOL}/{SYMBOL}-fundingRate-{month}.zip"
    return [(to_millis(r[0]), float(r[2])) for r in _read_csv(url) if len(r) >= 3]
