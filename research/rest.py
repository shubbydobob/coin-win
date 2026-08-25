"""REST 로 읽는다. **덤프가 못 덮는 자리에만 쓴다.**

덤프는 T-1 까지만 올라오므로 최근 하루~이틀과 지금 이 순간은 REST 가 메운다. 그리고 호가는
어떤 덤프로도 우리 정의로는 얻을 수 없어(Phase 0) 처음부터 여기서만 온다.

**호가 세 수는 자바 `OrderBook` 과 같은 식으로 낸다.** 다르게 계산하면 이 표의 결과를 화면에
되돌려 읽을 수 없다:

- 스프레드 = 최우선 매도가 − 최우선 매수가
- 두께 = 각 쪽 20단 잔량의 합
- 불균형 = (매수 − 매도) / (매수 + 매도)

**`futures/data` 계열은 간격을 두고 부른다.** 가중치 헤더가 없어 소비량이 보이지 않고,
Phase 0 조사 중 실제로 `-1003` IP 차단을 맞았다.
"""

import json
import time
import urllib.error
import urllib.parse
import urllib.request

from config import FAPI_BASE, FUTURES_DATA_MIN_INTERVAL_S, SPOT_BASE, SYMBOL

#: 우리 화면이 보는 단 수. 바뀌면 두께와 불균형의 뜻이 바뀐다.
BOOK_LEVELS = 20

_last_futures_data = [0.0]


class RestError(Exception):
    pass


def _get(base, path, **params):
    url = f"{base}{path}?{urllib.parse.urlencode(params)}" if params else f"{base}{path}"
    for attempt in range(4):
        try:
            with urllib.request.urlopen(url, timeout=60) as r:
                return json.loads(r.read())
        except urllib.error.HTTPError as e:
            body = e.read()[:200].decode(errors="replace")
            if e.code in (418, 429) or "-1003" in body:
                # 차단이다. 물러선다 — 재시도를 몰아치면 차단이 길어진다.
                time.sleep(30 * (attempt + 1))
                continue
            raise RestError(f"HTTP {e.code} {url} :: {body}") from e
        except OSError as e:
            time.sleep(2 * (attempt + 1))
            if attempt == 3:
                raise RestError(f"{url} :: {e}") from e
    raise RestError(f"재시도를 다 썼다: {url}")


def klines(market, interval, start_ms, end_ms):
    """`[start_ms, end_ms)` 의 캔들을 이어받아 전부 돌려준다."""
    base, path, cap = (
        (FAPI_BASE, "/fapi/v1/klines", 1500) if market == "perp"
        else (SPOT_BASE, "/api/v3/klines", 1000)
    )
    out, cursor = [], start_ms
    while cursor < end_ms:
        batch = _get(base, path, symbol=SYMBOL, interval=interval,
                     startTime=cursor, endTime=end_ms - 1, limit=cap)
        if not batch:
            break
        out += batch
        nxt = int(batch[-1][0]) + 1
        if nxt <= cursor:            # 진전이 없으면 무한 루프다
            break
        cursor = nxt
        if len(batch) < cap:
            break
    return out


def funding(start_ms, end_ms):
    out, cursor = [], start_ms
    while cursor < end_ms:
        batch = _get(FAPI_BASE, "/fapi/v1/fundingRate", symbol=SYMBOL,
                     startTime=cursor, endTime=end_ms - 1, limit=1000)
        if not batch:
            break
        out += [(int(r["fundingTime"]), float(r["fundingRate"])) for r in batch]
        nxt = int(batch[-1]["fundingTime"]) + 1
        if nxt <= cursor:
            break
        cursor = nxt
        if len(batch) < 1000:
            break
    return out


def _futures_data(path, **params):
    gap = time.monotonic() - _last_futures_data[0]
    if gap < FUTURES_DATA_MIN_INTERVAL_S:
        time.sleep(FUTURES_DATA_MIN_INTERVAL_S - gap)
    _last_futures_data[0] = time.monotonic()
    return _get(FAPI_BASE, path, **params)


#: `futures/data` 경로 → 스냅샷 칸 이름과 응답 키.
_METRIC_SOURCES = [
    ("/futures/data/openInterestHist", {
        "open_interest": "sumOpenInterest",
        "open_interest_value": "sumOpenInterestValue",
    }),
    ("/futures/data/globalLongShortAccountRatio", {
        "long_short_account_ratio": "longShortRatio",
    }),
    ("/futures/data/topLongShortAccountRatio", {
        "top_trader_account_ratio": "longShortRatio",
    }),
    ("/futures/data/topLongShortPositionRatio", {
        "top_trader_position_ratio": "longShortRatio",
    }),
    ("/futures/data/takerlongshortRatio", {
        "taker_buy_sell_ratio": "buySellRatio",
    }),
]


def metrics_5m(limit=500, start_ms=None, end_ms=None):
    """5분 격자 파생 통계. `{create_time_ms: {지표: 값}}`.

    **31일보다 오래된 `start_ms` 는 `-1130` 으로 거절된다.** 그것이 이 함수를 백필에 쓰지
    않는 이유다.
    """
    merged = {}
    for path, mapping in _METRIC_SOURCES:
        params = {"symbol": SYMBOL, "period": "5m", "limit": limit}
        if start_ms is not None:
            params["startTime"] = start_ms
        if end_ms is not None:
            params["endTime"] = end_ms
        for row in _futures_data(path, **params):
            ts = int(row["timestamp"])
            slot = merged.setdefault(ts, {})
            for column, key in mapping.items():
                slot[column] = float(row[key])
    return merged


def order_book(levels=BOOK_LEVELS):
    """지금 호가에서 스프레드·두께·불균형. `(값 묶음, 거래소 시각)`."""
    book = _get(FAPI_BASE, "/fapi/v1/depth", symbol=SYMBOL, limit=levels)
    bids = [(float(p), float(q)) for p, q in book["bids"][:levels]]
    asks = [(float(p), float(q)) for p, q in book["asks"][:levels]]
    if not bids or not asks:
        return None, None
    bid_depth = sum(q for _, q in bids)
    ask_depth = sum(q for _, q in asks)
    total = bid_depth + ask_depth
    return {
        "book_spread": asks[0][0] - bids[0][0],
        "book_bid_depth": bid_depth,
        "book_ask_depth": ask_depth,
        "book_imbalance": (bid_depth - ask_depth) / total if total else None,
    }, int(book.get("T") or book.get("E"))
