# ADR 017 — `journal` 이 `indicator.domain` 의 `BandPosition` 을 참조한다

- 상태: 채택
- 날짜: 2026-08-21

## 맥락

Phase 5 의 `MarketContext` 는 진입 시점의 시장 상태를 남긴다. 그중 구조화할 수 있는 것은
지표뿐이다 — 지지·저항의 코드 정의는 Phase 6 의 첫 작업이라 아직 없다.

지표 상태를 어떻게 적을 것인가. 실제로 필요한 값은 **가격이 일목 구름과 볼린저 밴드의
어느 쪽에 있었는가** 이고, 그것은 Phase 4 에서 이미 `indicator.domain.BandPosition`
(`ABOVE` / `INSIDE` / `BELOW`) 으로 확정돼 있다.

그런데 `architecture.md` 의 모듈 간 의존 목록에 `journal → indicator` 가 없다. 목록은
"그 외 모듈 간 직접 참조 금지" 로 닫혀 있다.

## 결정

**`journal.domain` 이 `indicator.domain.BandPosition` 을 참조한다.** `architecture.md` 의
모듈 간 의존 목록에 한 줄을 추가한다. `indicator` 의 나머지(계산기·`IchimokuValue`·
`CandleSeries`)는 참조하지 않는다 — `journal` 은 지표를 **계산하지 않고 결과만 적는다.**

## 근거

**세 선택지가 있었다.**

*(가) journal 이 자기 enum 을 갖는다.* 세 값이 같은 뜻으로 두 벌 생긴다. 그러면 "경계는
구간에 포함된다(`INSIDE`)" 는 규칙이 한쪽만 바뀌는 순간 두 모듈이 다른 답을 낸다.
`conventions.md` 의 "같은 로직이 두 번째 나타나면 즉시 추출한다" 에 정면으로 걸린다.
게다가 실사용 경로에서 두 enum 사이의 변환 코드가 반드시 필요해진다.

*(나) `BandPosition` 을 `common` 으로 올린다.* [ADR 013](013-candle-lives-in-market-domain.md)
이 `Candle` 에 대해 정확히 이 선택지를 거부했다. 이유도 같다 — `common` 이 반올림 정책을
가진 값 객체 모음에서 공용 모델 전반으로 넓어진다. `BandPosition` 은 반올림 정책이 없고
지표의 어휘다.

*(다) 채택 — `indicator.domain` 에 두고 journal 이 읽는다.* ADR 013 이 `Candle` 에 대해
내린 결정과 같은 형태다. **생산하는 모듈이 소유하고, 소비하는 모듈이 참조한다.**

**의존 방향이 안전하다.** `indicator` 는 `journal` 을 모르고 앞으로도 모른다. 지표는
매매 기록의 존재를 알 필요가 없다. 한 방향이므로 순환이 생기지 않고, ArchUnit 규칙 3 이
그것을 계속 확인한다.

**참조 범위가 좁다.** enum 하나다. `IchimokuValue` 나 `IchimokuCloud` 를 끌어오는 것이었다면
판단이 달랐을 것이다 — 그때는 `journal` 이 지표를 계산하게 되고, 기록해야 할 것은 그때의
판정 결과이지 오늘 다시 계산한 값이 아니다.

## 한계

- **모듈 간 의존은 ArchUnit 이 강제하지 않는다.** 여섯 규칙 중 어느 것도 모듈 경계를 검사하지
  않으므로, 이 결정은 문서와 리뷰가 지킨다. 규칙 3(순환 참조)이 최악의 경우만 막는다.
  모듈 간 의존을 규칙으로 세우는 것은 Phase 6 에서 `backtest` 가 여러 모듈을 조합할 때
  다시 볼 문제다.
- **진입 시점의 판정을 그대로 믿는다.** 저장되는 것은 사람이 적어 넣은 `BandPosition` 이지
  캔들에서 다시 계산한 값이 아니다. 잘못 적으면 잘못 남는다. 캔들로 검산하려면 `journal` 이
  `market` 과 `indicator` 를 모두 끌어와야 하고, 그것은 기록 모듈의 일이 아니다.

## 결과

- `MarketContext(priceAtEntry, ichimokuPosition, bollingerPosition, rationale)`.
- `architecture.md` 모듈 간 의존에 `journal → position, indicator.domain` 이 적힌다.
- 지지·저항은 `rationale` 자유 텍스트다. Phase 6 에서 저항대가 코드로 정의되면 그때
  구조화하며, 그전에 스키마를 박으면 이미 쌓인 기록을 통째로 못 쓰게 된다.

## 관련

- [013 — `Candle` 은 `market.domain` 에 산다](013-candle-lives-in-market-domain.md) — 같은 형태의 판단
- [014 — 일목 변위와 후행스팬](014-ichimoku-displacement-and-lagging-span.md) — `BandPosition` 이 확정된 자리
- `.claude/docs/roadmap.md` Phase 6 — 저항대의 코드 정의
