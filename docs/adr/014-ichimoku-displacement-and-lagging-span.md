# ADR 014 — 일목 변위 26 은 25봉을 민다, 그리고 후행스팬만 `Optional` 이다

- 상태: 채택 (변위는 **트레이딩뷰 Pine 소스 원문으로 확정**)
- 날짜: 2026-08-21

## 맥락

일목균형표의 다섯 선 중 셋은 시간 축이 어긋나 있다.

- 선행스팬 1·2 — 계산 시점보다 **앞**에 그려진다
- 후행스팬 — 계산 시점보다 **뒤**에 그려진다

그래서 "index `i` 의 일목 값" 이 무엇을 가리키는지가 먼저 정해져야 하고, 여기에 두 가지가 얽힌다.

**첫째, 변위가 몇 봉인가.** 바이낸스·트레이딩뷰 문서는 "26 periods" 라고만 적는다. 그런데
트레이딩뷰 내장 지표의 플롯 오프셋이 `displacement - 1` 즉 25 라는 설명이 널리 퍼져 있다.
차트에 그리는 위치의 문제이면서 동시에 "지금 봉의 구름이 몇 봉 전 계산값인가" 를 정하므로
**값에 영향을 준다.** 초안에서는 이것을 확정하지 못해 기본값 26 을 잠정으로 두었다.

**둘째, 최근 몇 봉에는 후행스팬이 없다.** 변위만큼 뒤의 종가가 아직 존재하지 않기 때문이다.
그런데 실사용에서 가장 자주 보는 값은 *가장 최근 봉의 구름 위치* 다.

## 결정

**변위 입력은 26 으로 두고, 실제 이동은 25 로 한다** (`shift() = displacement - 1`).

**변위 적용값을 낸다.** `IchimokuValue` 의 선행스팬 둘은 그 시점에 유효한 구름이며,
`positionOf(현재가)` 에 지금 가격을 그대로 넣으면 된다.

**후행스팬만 `Optional<Price>` 로 둔다.** 나머지 넷은 필수다.

**마지막 캔들 이후의 "미래 구름" 은 내지 않는다.**

## 근거

### 변위 — 트레이딩뷰가 배포하는 Pine 소스로 확정했다

추측이 아니라 원문이다. 트레이딩뷰는 내장 지표의 Pine 소스를 자체 엔드포인트로 배포한다
(`pine-facade.tradingview.com/pine-facade/get/STD;Ichimoku%1Cloud/34.0`). "Ichimoku Cloud"
v34.0 의 해당 부분은 이렇다.

```pine
//@version=6
conversionPeriods    = input.int(9,  minval=1, title="Conversion Line Length")
basePeriods          = input.int(26, minval=1, title="Base Line Length")
laggingSpan2Periods  = input.int(52, minval=1, title="Leading Span B Length")
displacement         = input.int(26, minval=1, title="Lagging Span")

donchian(len) => math.avg(ta.lowest(len), ta.highest(len))
conversionLine = donchian(conversionPeriods)
baseLine       = donchian(basePeriods)
leadLine1      = math.avg(conversionLine, baseLine)
leadLine2      = donchian(laggingSpan2Periods)

plot(conversionLine, color=#2962FF, title="Conversion Line")
plot(baseLine,       color=#B71C1C, title="Base Line")
plot(close,  offset = -displacement + 1, color=#43A047, title="Lagging Span")
p1 = plot(leadLine1, offset =  displacement - 1, ...)
p2 = plot(leadLine2, offset =  displacement - 1, ...)
```

읽히는 것이 넷이다.

1. **선행스팬 오프셋은 `displacement - 1` = 25 다.** 26 이 아니다. 현재 봉을 1 번째로 세는
   관습에서 나온 값이다. 26 을 밀면 구름 전체가 한 칸 뒤로 밀리는데, **그 상태로도 모든 값이
   그럴듯해 보인다** — 어긋남이 드러나지 않는 종류의 오차다.
2. **후행스팬 오프셋은 `-displacement + 1` = −25 로 선행스팬과 대칭이다.** 그래서 구현도
   같은 `shift()` 하나를 쓴다.
3. `donchian(len) = math.avg(ta.lowest(len), ta.highest(len))` — 구간 최저·최고의 평균이다.
   전환선·기준선·선행스팬 2 가 전부 이 하나이고 기간만 다르다. 우리 `midpoint` 와 같다.
4. 전환선과 기준선에는 **오프셋이 없다.** 변위 논쟁과 무관하게 맞아야 하는 값이라는 뜻이다.

**입력을 25 로 바꾸지 않고 26 을 유지한 이유.** 트레이딩뷰 UI 도 사용자에게 26 을 받고 내부에서
25 를 민다. 우리가 26 을 25 로 바꿔 저장하면, 차트에서 26 을 본 사람이 코드에서 25 를 보게 되고
그 불일치가 볼 때마다 다시 검증된다. 어휘는 맞추고 변환은 한 곳(`shift()`)에 가둔다.

### 후행스팬 하나 때문에 나머지 넷을 버리지 않는다

다섯 선이 모두 확정된 구간만 값을 내면 뒤 25봉이 통째로 잘리고, 그 25봉 안에 *지금* 이 들어
있다. 매매 보조 도구에서 가장 필요한 값을 워밍업 규칙의 부수 효과로 잃는 것은 본말전도다.
`Optional` 은 "이 시점에 후행스팬은 아직 없다" 를 타입으로 말하므로, 호출부가 인덱스 산술로
같은 사실을 재발견할 필요가 없다.

### 미래 구름을 내지 않는 이유는 시간 축이다

마지막 캔들 이후 25봉의 시각을 만들려면 캔들 주기를 알아야 하는데, `Candle` 은 주기를 들지
않는다(주기는 `CandleQuery` 의 성질이다). `IchimokuCloud` 에 `CandleInterval` 을 끌고
들어오면 순수 계산이 시장 데이터 질의 어휘에 의존하게 된다. 지금 필요한 것은 "현재 가격이
구름 위인가" 이고 그것은 미래 구름 없이 나온다. 차트에 구름을 그려야 하는 Phase 8 에서 근거를
갖고 다시 판단한다.

## 확인한 것과 확인하지 않은 것

**확인했다.** 트레이딩뷰가 배포하는 Pine 소스 원문(Ichimoku Cloud v34.0). 공식·오프셋·입력
기본값이 전부 우리 구현과 같다. 값 몇 개를 눈으로 맞대는 것보다 강한 증거다 — 특정 시점이
아니라 **모든 입력에 대해** 성립하기 때문이다.

**확인하지 않았다.** 차트에 실제로 렌더링된 수치와의 대조. 브라우저에서 차트 캔버스가 뜨지
않아 십자선을 올리지 못했다. 소스가 같으므로 값도 같지만, 그것은 연역이지 관측이 아니다.
`.\gradlew.bat crossCheck` 가 실제 캔들의 우리 값을 찍으므로, 차트를 볼 수 있을 때 한 번
맞대 보면 이 항목이 닫힌다.

## 한계

- **소스는 버전이 붙어 있다(v34.0, 2026-08-12).** 트레이딩뷰가 내장 지표를 고치면 어긋날 수
  있다. 변위를 설정 필드로 남겨 둔 이유이기도 하다 — 바뀌면 숫자 하나를 고친다.
- 후행스팬이 `Optional` 이므로 소비자는 매번 존재 여부를 다뤄야 한다. 후행스팬을 조건에 쓰는
  전략(Phase 6)은 최근 25봉에서 판단을 보류하거나 다른 근거를 써야 한다. 이것은 표현의
  결함이 아니라 **지표 자체의 성질**이다.
- 구름 위치 판정에서 **경계는 구름에 포함**된다(`INSIDE`). 상단에 정확히 닿은 종가를 돌파로
  치면 신호가 과하게 나온다는 판단이며, 트레이딩뷰의 시각적 판정과 다를 수 있다.

## 관련

- [013 — `Candle` 은 `market/domain` 에 산다](013-candle-lives-in-market-domain.md)
- [015 — 지표 golden test 의 기준값](015-indicator-golden-values.md)
- `.claude/skills/domain-model/SKILL.md` — 지표 절을 이 결정에 맞춰 고쳤다
